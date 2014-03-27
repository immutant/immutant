;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.messaging
  "Easily publish and receive messages containing any type of nested
   data structure to dynamically-created topics and queues. Message
   distribution is automatically load-balanced when clustered."
  (:use [immutant.util :only (at-exit mapply waiting-derefable maybe-deref validate-options)]
        [immutant.messaging.core :exclude [with-connection]]
        immutant.messaging.internal)
  (:require [immutant.messaging.codecs :as codecs]
            [immutant.registry         :as registry]
            [immutant.logging          :as log]))

(defn as-queue
  "Marks the given queue name as a queue. Useful for working with queues that
   don't follow the Immutant convention of containing \"queue\" in the name.
   The result can be passed to any immutant.messaging functions that take a
   queue name."
  [^String name]
  (->QueueMarker name))

(defn as-topic
  "Marks the given topic name as a topic. Useful for working with topics that
   don't follow the Immutant convention of containing \"topic\" in the name.
   The result can be passed to any immutant.messaging functions that take a
   topic name."
  [^String name]
  (->TopicMarker name))

(defn start
  "Create a message destination; name should contain either 'queue'
   or 'topic', or be the result of calling as-queue or as-topic. If
   a :selector is provided, then only messages with
   metadata/properties matching that expression will be accepted for
   delivery.

   The following options are supported for queues only [default]:
     :durable    whether messages persist across restarts [true]
     :selector   a JMS (SQL 92) expression to filter published messages [nil]

   Additionally, you can pass any of the options expected by
   immutant.messaging.hornetq/set-address-options and they
   will be applied to the created destination."
  
  [name & opts]
  (cond
   (queue-name? name) (apply start-queue (.toString name) opts)
   (topic-name? name) (apply start-topic (.toString name) opts)
   :else (throw (destination-name-error name))))

(defmacro
  ^{:valid-options
    #{:xa :username :password :connection :client-id :host :port
      :retry-interval :retry-interval-multiplier :max-retry-interval
      :reconnect-attempts}}
  with-connection
  "Can be used to set default options for the messaging functions
   called within its body. More importantly, the nested calls will
   re-use the JMS Connection created by this function unless the
   nested calls' connection-related options differ."
  [options & body]
  `(let [opts# (validate-options with-connection ~options)]
    (immutant.messaging.core/with-connection (fn [] ~@body) opts#)))

(defn
  ^{:valid-options
    #{:encoding :priority :ttl :persistent :properties :correlation-id
      :host :port :username :password :connection}}
  publish
  "Send a message to a destination. dest can either be the name of the
   destination, a javax.jms.Destination, or the result of as-queue or
   as-topic. If the message is a javax.jms.Message, then the message
   is sent without modification.  If the message contains metadata, it
   will be transferred as JMS properties and reconstituted upon
   receipt. Metadata keys must be valid Java identifiers (because they
   can be used in selectors) and can be overridden using
   the :properties option. Returns the JMS message object that was
   published.

   The following options are supported [default]:
     :encoding        :clojure :edn :fressian :json or :text [:edn]
     :priority        0-9 or :low :normal :high :critical [4]
     :ttl             time to live, in ms [0=forever]
     :persistent      whether undelivered messages survive restarts [true]
     :properties      a map to which selectors may be applied, overrides metadata [nil]
     :correlation-id  used to set the JMSCorrelationID [nil]
                      see http://docs.oracle.com/javaee/6/api/javax/jms/Message.html#setJMSCorrelationID(java.lang.String) 
     :host            the remote host to connect to (default is to connect in-vm)
                      [nil]
     :port            the remote port to connect to (requires :host to be set)
                      [nil, or 5445 if :host is set]
     :username        the username to use to auth the connection (requires :password
                      to be set) [nil]
     :password        the password to use to auth the connection (requires :username
                      to be set) [nil]
     :connection      a JMS Connection to use; caller expected to close [nil]"
  [dest message & {:as opts}]
  (let [opts (validate-options publish opts)]
    (with-connection opts
      (let [opts (options opts)
            session (session)
            destination (create-destination session dest)
            producer (.createProducer session destination)
            encoded (if (instance? javax.jms.Message message)
                      message
                      (-> (codecs/encode session message opts)
                        (set-properties! (meta message))
                        (set-properties! (:properties opts))
                        (set-attributes! opts)))
            {:keys [delivery priority ttl]} (wash-publish-options opts producer)]
        (.send producer encoded delivery priority ttl)
        encoded))))

(defn ^{:valid-options
        #{:timeout :timeout-val :selector :decode? :client-id :host :port
          :username :password :connection}}
  receive
  "Receive a message from a destination. dest can either be the name
   of the destination, a javax.jms.Destination, or the result of
   as-queue or as-topic. If a :selector is provided, then only
   messages having metadata/properties matching that expression may be
   received.

   The following options are supported [default]:
     :timeout     time in ms, after which the timeout-val is returned. 0
                  means wait forever, -1 means don't wait at all [10000]
     :timeout-val the value to return when a timeout occurs. Also returned when
                  a timeout of -1 is specified, and no message is available [nil]
     :selector    A JMS (SQL 92) expression matching message metadata/properties [nil]
     :decode?     if true, the decoded message body is returned. Otherwise, the
                  javax.jms.Message object is returned [true]
     :client-id   identifies a durable topic subscriber, ignored for queues [nil]
     :host        the remote host to connect to (default is to connect in-vm) [nil]
     :port        the remote port to connect to (requires :host to be set) [nil, or
                  5445 if :host is set]
     :username    the username to use to auth the connection (requires :password to
                  be set) [nil]
     :password    the password to use to auth the connection (requires :username to
                  be set) [nil]
     :connection  a JMS Connection to use; caller expected to close [nil]"
  [dest & {:as opts}]
  (let [opts (validate-options receive opts)]
    (with-connection opts
      (let [opts (options opts)
            {:keys [timeout timeout-val decode?] :or {timeout 10000 decode? true}} opts
            session (session)
            destination (create-destination session dest)
            consumer (create-consumer session destination opts)
            message (if (= -1 timeout)
                      (.receiveNoWait consumer)
                      (.receive consumer timeout))]
        (if message
          (codecs/decode-if decode? message)
          timeout-val)))))

(defn message-seq
  "A lazy sequence of messages received from a destination. Accepts
   same options as receive."
  [dest & {:as opts}]
  (let [opts (validate-options message-seq receive opts)]
    (lazy-seq (cons (mapply receive dest opts)
                (mapply message-seq dest opts)))))

(def ^:dynamic *raw-message*
  "Will be bound to the raw javax.jms.Message during the invocation of a
   destination listener."
  nil)

(defn- remote-listen [dest {:keys [izer connection setup-fn]} {:keys [concurrency]}]
  (try
    (when izer
      (log/info "Creating direct listener for remote destination:" dest))
    (dotimes [_ concurrency]
      (let [{session     "session"
             consumer-fn "consumer-fn"
             handler     "handler"} (setup-fn)]
        (.setMessageListener (consumer-fn session)
          (create-listener handler))))
    (.start connection)
    [connection (destination-name dest)]
    (catch Throwable e
      (close-connection connection)
      (throw e))))

(defn- in-vm-listen [dest
                     listener-fn
                     {:keys [izer connection listener-name setup-fn]}
                     {:keys [concurrency] :as opts}]
  (log/info "Creating in-vm listener for:" dest)
  (let [dest-name (destination-name dest)
        group (.createGroup
                izer
                dest-name
                false ;; TODO: singleton
                concurrency
                (not (nil? (:client-id opts)))
                (str (:selector opts)
                  (if (topic? dest)
                    (str (:client-id opts) listener-fn)))
                connection
                setup-fn)]
    (waiting-derefable #(.hasStartedAtLeastOnce group) [group dest-name])))

(defn- remote-listen? [dest {:keys [izer connection]} {:keys [host]}]
  (or (not izer)
    (and host (destination-exists? connection dest))))

(defn- create-setup-fn [dest f connection {:keys [decode?] :as opts}]
  (bound-fn []
    (let [session (create-session connection)
          destination (create-destination session dest)]
      {"session" session
       "consumer-fn" #(create-consumer % destination opts)
       "handler" (with-loading-context
                   (bound-fn [m]
                     (binding [*raw-message* m]
                       (f (codecs/decode-if decode? m)))))})))

(defn ^{:valid-options
        #{:concurrency :xa :selector :decode? :client-id
          :host :port :username :password
          :retry-interval :retry-interval-multiplier
          :max-retry-interval :reconnect-attempts}}
  listen
  "The handler function, f, will receive each message sent to dest.
   dest can either be the name of the destination, a
   javax.jms.Destination, or the result of as-queue or as-topic. If
   a :selector is provided, then only messages having
   metadata/properties matching that expression may be received.

   listen is asynchronous - if you need to synchronize on its
   completion, you should deref the result.

   The following options are supported [default]:
     :concurrency  the number of threads handling messages [1]
     :xa           Whether the handler demarcates an XA transaction [true]
     :selector     A JMS (SQL 92) expression matching message metadata/properties [nil]
     :decode?      if true, the decoded message body is passed to f. Otherwise, the
                   javax.jms.Message object is passed [true]
     :client-id    identifies a durable topic subscriber, ignored for queues [nil]
     :host         the remote host to connect to (default is to connect in-vm) [nil]
     :port         the remote port to connect to (requires :host to be set) [nil,
                   or 5445 if :host is set]
     :username     the username to use to auth the connection (requires :password
                   to be set) [nil]
     :password     the password to use to auth the connection (requires :username
                   to be set) [nil]

  The following options are for connection reconnection/reattachment attributes:
     :retry-interval             the period in milliseconds between subsequent
                                 reconnection attempts
     :retry-interval-multiplier  a multiplier to apply to the time since the last
                                 retry to compute the time to the next retry
     :max-retry-interval         the max retry interval that will be used [2000]
     :reconnect-attempts         total number of reconnect attempts to make before giving
                                 up and shutting down (-1 for unlimited) [0]"
  [dest f & {:as opts}]
  (let [opts (merge {:concurrency 1 :decode? true}
               (validate-options listen (options opts)))
        connection (create-connection (merge {:xa (nil? (:host opts))} opts))
        env {:connection connection
             :izer (registry/get "message-processor-groupizer")
             :setup-fn (create-setup-fn dest f connection opts)}]
    (at-exit #(close-connection connection))
    (cond
     (remote-listen? dest env opts) (remote-listen dest env opts)
     (destination-exists? connection dest) (in-vm-listen dest f env opts)
     :else (throw (IllegalStateException.
                    (str "Destination " (destination-name dest) " does not exist."))))))

(defn request
  "Send a message to queue and return a delay that will retrieve the response.
   Implements the request-response pattern, and is used in conjunction
   with respond. The queue parameter can either be the name of a
   queue, an actual javax.jms.Queue, or the result of as-queue.

   It takes the same options as publish."
  [queue message & {:as opts}]
  {:pre [(queue? queue)]}
  (let [opts (validate-options request publish opts)
        ^javax.jms.Message message (mapply publish queue message
                                           (update-in opts [:properties]
                                                      #(merge % {"synchronous" "true"})))]
    (delayed
      (fn [t t-val]
        (mapply
          receive
          queue
          (assoc opts
            :timeout t
            :timeout-val t-val
            :selector (str "JMSCorrelationID='" (.getJMSMessageID message) "'")))))))

(defn ^{:valid-options
        (conj (-> #'listen meta :valid-options) :ttl)}
  respond
  "Listen for messages on queue sent by the request function and
   respond with the result of applying f to the message. queue can
   either be the name of the queue, a javax.jms.Queue, or the result
   of as-queue. Accepts the same options as listen, along with [default]:

     :ttl  time for the response mesage to live, in ms [60000, 1 minute]"
  [queue f & {:keys [decode? ttl]
              :or {decode? true, ttl 60000} ;; 1m
              :as opts}]
  {:pre [(queue? queue)]}
  (let [opts (validate-options respond opts)]
    (letfn [(respond* [^javax.jms.Message msg]
              (publish (.getJMSDestination msg)
                (f (codecs/decode-if decode? msg))
                :correlation-id (.getJMSMessageID msg)
                :ttl ttl
                :encoding (codecs/get-encoding msg)))]
      (mapply listen queue respond*
        (assoc (update-in opts [:selector]
                 #(str "synchronous = 'true'"
                    (when % (str " and " %))))
          :decode? false)))))

(defn ^{:valid-options
        (-> #'with-connection meta :valid-options (conj :subscriber-name))}
  unsubscribe
  "Used when durable topic subscribers are no longer interested. This
   cleans up some server-side state, but since it'll be be deleted
   anyway when the topic is stopped, it's an optional call."
  [client-id & {:keys [subscriber-name] :or {subscriber-name default-subscriber-name} :as opts}]
  (with-connection (assoc (validate-options unsubscribe opts) :client-id client-id)
    (.unsubscribe (session) subscriber-name)))

(defn unlisten
  "Pass the result of a call to listen or respond to de-register the handler.
   You only need to do this if you wish to stop the handler's
   destination before your app is undeployed.

   unlisten is asynchronous - if you need to synchronize on its
   completion, you should deref the result. The result of the
   call. The deref will resolve to a boolean that is true if there was
   actually something to unlisten, false otherwise."
  [listener]
  (when-let [[group dest-name] (maybe-deref listener)]
    (log/info "Removing listener for:" dest-name)
    (future
      (if (or (instance? java.io.Closeable group)
              (instance? javax.jms.Connection group))
        (do
          (.close group)
          true)
        (.remove group true)))))

(defn ^{:valid-options #{:force}}
  stop
  "Destroy a message destination. Typically not necessary since it
   will be done for you when your app is undeployed. This will fail
   with a warning if any handlers are listening or any messages are
   yet to be delivered unless ':force true' is passed. Returns true on
   success."
  [name & {:keys [force] :as opts}]
  (validate-options stop opts)
  (cond
   (queue-name? name) (stop-queue name :force force)
   (topic-name? name) (stop-topic name :force force)
   :else (throw (destination-name-error name))))



