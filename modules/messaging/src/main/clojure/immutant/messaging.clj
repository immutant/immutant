;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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
  (:use [immutant.util :only (at-exit mapply)]
        [immutant.messaging.core])
  (:require [immutant.messaging.codecs :as codecs]
            [immutant.registry         :as registry]
            [clojure.tools.logging     :as log])
  (:import [immutant.messaging.core QueueMarker TopicMarker]))

(defn as-queue
  "Marks the given queue name as a queue. Useful for working with queues that
   don't follow the Immutant convention of containing \"queue\" in the name.
   The result can be passed to any immutant.messaging functions that take a
   queue name."
  [^String name]
  (QueueMarker. name))

(defn as-topic
  "Marks the given topic name as a topic. Useful for working with topics that
   don't follow the Immutant convention of containing \"topic\" in the name.
   The result can be passed to any immutant.messaging functions that take a
   topic name."
  [^String name]
  (TopicMarker. name))

(defn start
  "Create a message destination; name should begin with either 'queue'
   or 'topic', or be the result of calling as-queue or as-topic.

   The following options are supported [default]:
     :durable    whether queue items persist across restarts [true]
     :selector   A JMS (SQL 92) expression matching message metadata/properties [\"\"]"
  [name & opts]
  (cond
   (queue-name? name) (apply start-queue (.toString name) opts)
   (topic-name? name) (apply start-topic (.toString name) opts)
   :else (throw (destination-name-error name))))

(defn publish
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
     :encoding        :clojure :json or :text [:clojure]
     :priority        0-9 or :low :normal :high :critical [4]
     :ttl             time to live, in ms [0=forever]
     :persistent      whether undelivered messages survive restarts [true]
     :properties      a hash to which selectors may be applied, overrides metadata [nil]
     :correlation-id  used to set the JMSCorrelationID [nil]
                      see http://docs.oracle.com/javaee/6/api/javax/jms/Message.html#setJMSCorrelationID(java.lang.String) 
     :host            the remote host to connect to (default is to connect in-vm)
                      [nil]
     :port            the remote port to connect to (requires :host to be set)
                      [nil, or 5445 if :host is set]
     :username        the username to use to auth the connection (requires :password
                      to be set) [nil]
     :password        the password to use to auth the connection (requires :username
                      to be set) [nil]"
  [dest message & {:as opts}]
  (with-connection opts
    (let [session (session)
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
      encoded)))

(defn receive
  "Receive a message from a destination. dest can either be the name
   of the destination, a javax.jms.Destination, or the result of
   as-queue or as-topic.

   The following options are supported [default]:
     :timeout    time in ms, after which nil is returned. 0 means wait forever,
                 -1 means don't wait at all [10000]
     :selector   A JMS (SQL 92) expression matching message metadata/properties
     :decode?    if true, the decoded message body is returned. Otherwise, the
                 javax.jms.Message object is returned [true]
     :client-id  identifies a durable topic subscriber, ignored for queues [nil]
     :host       the remote host to connect to (default is to connect in-vm) [nil]
     :port       the remote port to connect to (requires :host to be set) [nil, or
                 5445 if :host is set]
     :username   the username to use to auth the connection (requires :password to
                 be set) [nil]
     :password   the password to use to auth the connection (requires :username to
                 be set) [nil]"
  [dest & {:keys [timeout decode?] :or {timeout 10000 decode? true} :as opts}]
  (with-connection opts
    (let [session (session)
          destination (create-destination session dest)
          consumer (create-consumer session destination opts)
          message (if (= -1 timeout)
                    (.receiveNoWait consumer)
                    (.receive consumer timeout))]
      (when message
        (codecs/decode-if decode? message)))))

(defn ^:internal ^:no-doc delayed-receive
  "Creates an timeout-derefable delay around a receive call"
  [queue & {:as opts}]
  (let [val (atom nil)
        rcv (fn [timeout]
              (reset! val
                      (mapply receive queue (assoc opts :timeout timeout))))]
    (proxy [clojure.lang.Delay clojure.lang.IBlockingDeref] [nil]
      (deref
        ([]
           (if (nil? @val) (rcv 0) @val))
        ([timeout-ms timeout-val]
           (if (nil? @val)
             (let [r (rcv timeout-ms)]
               (if (nil? r) timeout-val r))
             @val)))
      (isRealized []
        (not (and (nil? @val)
                  (nil? (rcv -1))))))))

(defn message-seq
  "A lazy sequence of messages received from a destination. Accepts
   same options as receive."
  [dest & opts]
  (lazy-seq (cons (apply receive dest opts) (message-seq dest))))

(def ^:dynamic *raw-message*
  "Will be bound to the raw javax.jms.Message during the invocation of a
   destination listener."
  nil)

(defn listen
  "The handler function, f, will receive each message sent to dest.
   dest can either be the name of the destination, a
   javax.jms.Destination, or the result of as-queue or as-topic.

   The following options are supported [default]:
     :concurrency  the number of threads handling messages [1]
     :selector     A JMS (SQL 92) expression matching message metadata/properties
     :decode?      if true, the decoded message body is passed to f. Otherwise, the
                   javax.jms.Message object is passed [true]
     :client-id    identifies a durable topic subscriber, ignored for queues [nil]
     :host         the remote host to connect to (default is to connect in-vm) [nil]
     :port         the remote port to connect to (requires :host to be set) [nil,
                   or 5445 if :host is set]
     :username     the username to use to auth the connection (requires :password
                   to be set) [nil]
     :password     the password to use to auth the connection (requires :username
                   to be set) [nil]"
  [dest f & {:keys [concurrency decode?] :or {concurrency 1 decode? true} :as opts}]
  (let [connection (create-connection opts)
        dest-name (destination-name dest)
        izer (registry/get "message-processor-groupizer")
        setup-fn (fn []
                   (let [session (create-session connection)
                         destination (create-destination session dest)]
                     {"session" session
                      "consumer" (create-consumer session destination opts)
                      "handler" #(with-transaction session
                                   (binding [*raw-message* %]
                                     (f (codecs/decode-if decode? %))))}))]
    (at-exit #(.close connection))
    (cond
     (or (not izer)
         (and (:host opts)
              (destination-exists? connection dest)))
     ;; we're outside the container, or in-container but listening to a remote dest
     (try
       (when izer (log/info
                   "Setting up direct listener for remote destination:"
                   dest))
       (dotimes [_ concurrency]
         (let [settings (setup-fn)]
           (.setMessageListener (settings "consumer")
                                (create-listener
                                 (settings "handler")))))
       (.start connection)
       connection
       (catch Throwable e
         (.close connection)
         (throw e)))

     (destination-exists? connection dest)
     ;; we're inside the container, and the dest is valid
     (let [complete (promise)
           group (.createGroup izer
                               dest-name
                               false ;; TODO: singleton
                               concurrency
                               (not (nil? (:client-id opts)))
                               (str (:selector opts) (if (topic? dest) (str (:client-id opts) f)))
                               connection
                               setup-fn
                               #(deliver complete %))]
       (if (= "up" (deref complete 5000 nil))
         group
         (log/error "Failed to setup listener for" dest-name)))

     :else
     (throw (IllegalStateException. (str "Destination " dest-name " does not exist."))))))

(defn request
  "Send a message to queue and return a delay that will retrieve the response.
   Implements the request-response pattern, and is used in conjunction
   with respond. The queue parameter can either be the name of a
   queue, an actual javax.jms.Queue, or the result of as-queue.

   It takes the same options as publish."
  [queue message & {:as opts}]
  {:pre [(queue? queue)]}
  (let [^javax.jms.Message message (mapply publish queue message
                                           (update-in opts [:properties]
                                                      #(merge % {"synchronous" "true"})))]
    (mapply delayed-receive queue
            (assoc opts
              :selector (str "JMSCorrelationID='" (.getJMSMessageID message) "'")))))

(defn respond
  "Listen for messages on queue sent by the request function and
   respond with the result of applying f to the message. queue can
   either be the name of the queue, a javax.jms.Queue, or the result
   of as-queue. Accepts the same options as listen, along with [default]:

     :ttl  time for the response mesage to live, in ms [60000, 1 minute]"
  [queue f & {:keys [decode? ttl]
              :or {decode? true, ttl 60000} ;; 1m
              :as opts}]
  {:pre [(queue? queue)]}
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
              :decode? false))))

(defn unsubscribe
  "Used when durable topic subscribers are no longer interested. This
   cleans up some server-side state, but since it'll be be deleted
   anyway when the topic is stopped, it's an optional call"
  [client-id & {:keys [subscriber-name] :or {subscriber-name default-subscriber-name} :as opts}]
  (with-connection (assoc opts :client-id client-id)
    (.unsubscribe (session) subscriber-name)))

(defn unlisten
  "Pass the result of a call to listen or respond to de-register the handler.
   You only need to do this if you wish to stop the handler's
   destination before your app is undeployed."
  [listener]
  (when listener
    (if (or (instance? java.io.Closeable listener)
            (instance? javax.jms.Connection listener))
      (.close listener)
      (let [complete (promise)]
        (.remove listener #(deliver complete %))
        (when-not (= "removed" (deref complete 5000 nil))
          (log/error "Failed to remove listener" listener))))))

(defn stop
  "Destroy a message destination. Typically not necessary since it
   will be done for you when your app is undeployed. This will fail
   with a warning if any handlers are listening or any messages are
   yet to be delivered unless ':force true' is passed. Returns true on
   success."
  [name & {:keys [force]}]
  (cond
   (queue-name? name) (stop-queue name :force force)
   (topic-name? name) (stop-topic name :force force)
   :else (throw (destination-name-error name))))



