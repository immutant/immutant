;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
        [immutant.try :only (try-defn)]
        [immutant.messaging.core])
  (:require [immutant.messaging.codecs :as codecs]
            [immutant.registry         :as registry]
            [clojure.tools.logging     :as log]))

(defn start
  "Create a message destination; name should begin with either 'queue'
   or 'topic'

   The following options are supported [default]:
     :durable    whether queue items persist across restarts [false]
     :selector   A JMS (SQL 92) expression matching message metadata/properties [\"\"]"
  [name & opts]
  (cond
   (queue-name? name) (apply start-queue name opts)
   (topic-name? name) (apply start-topic name opts)
   :else (throw (Exception. "Destination names must contain the word 'queue' or 'topic'"))))

(defn publish
  "Send a message to a destination. dest can either be the name of the
   destination or a javax.jms.Destination. If the message is a
   javax.jms.Message, then the message is sent without modification.
   If the message contains metadata, it will be transferred as JMS
   properties and reconstituted upon receipt. Metadata keys must be
   valid Java identifiers (because they can be used in selectors) and
   can be overridden using the :properties option. Returns the JMS
   message object that was published.

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
  [name-or-dest message & {:as opts}]
  (with-connection opts
    (let [session (session)
          destination (create-destination session name-or-dest)
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
   of the destination or a javax.jms.Destination.

   The following options are supported [default]:
     :timeout    time in ms, after which nil is returned [10000]
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
  [name-or-dest & {:keys [timeout decode?] :or {timeout 10000 decode? true} :as opts}]
  (with-connection opts
    (let [session (session)
          destination (create-destination session name-or-dest)
          consumer (create-consumer session destination opts)
          encoded (.receive consumer timeout)]
      (when encoded
        (codecs/decode-if decode? encoded)))))

(defn message-seq
  "A lazy sequence of messages received from a destination. Accepts
   same options as receive"
  [dest & opts]
  (lazy-seq (cons (apply receive dest opts) (message-seq dest))))

(defn listen
  "The handler function, f, will receive each message sent to dest.
   dest can either be the name of the destination or a
   javax.jms.Destination.

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
  [name-or-dest f & {:keys [concurrency decode?] :or {concurrency 1 decode? true} :as opts}]
  (let [connection (create-connection opts)
        dest-name (destination-name name-or-dest)
        setup-fn (fn []
                   (let [session (create-session connection)
                         destination (create-destination session name-or-dest)]
                     {"session" session
                      "consumer" (create-consumer session destination opts)
                      "handler" #(with-transaction session
                                   (f (codecs/decode-if decode? %)))}))]
    (at-exit #(.close connection))
    (if-let [izer (registry/get "message-processor-groupizer")]
      
      ;; in-container
      (if (destination-exists? connection dest-name)
        (let [complete (promise)
              group (.createGroup izer
                                  dest-name
                                  false ;; TODO: singleton
                                  concurrency
                                  (not (nil? (:client-id opts)))
                                  (.toString f)
                                  connection
                                  setup-fn
                                  #(deliver complete %))]
          (if (= "up" (deref complete 5000 nil))
            group
            (log/error "Failed to setup listener for" dest-name)))
        (throw (IllegalStateException. (str "Destination " dest-name " does not exist."))))
      
      ;; out of container
      (try
        (dotimes [_ concurrency]
          (let [settings (setup-fn)]
            (.setMessageListener (settings "consumer")
                                 (create-listener (settings "handler")))))
        (.start connection)
        connection
        (catch Throwable e
          (.close connection)
          (throw e))))))

(defn pipeline [opts-or-fn & fns]
  (let [[opts fns] (if (fn? opts-or-fn)
                     [opts-or-fn fns]
                     [nil (cons opts-or-fn fns)])
        queue-name (str "queue.pipeline-" (java.util.UUID/randomUUID))]
    (start queue-name)
    
    
    ))

(defn request
  "Send a message to queue and return a delay that will retrieve the response.
   Implements the request-response pattern, and is used in conjunction
   with respond. The queue parameter can either be the name of a
   queue or an actual javax.jms.Queue.

   It takes the same options as publish, and one more [default]:
     :timeout  time in ms for the delayed receive to wait once it it is
               dereferenced, after which nil is returned [10000]"
  [queue message & {:as opts}]
  {:pre [(queue? queue)]}
  (let [^javax.jms.Message message (mapply publish queue message
                                           (update-in opts [:properties]
                                                      #(merge % {"synchronous" "true"})))]
    (delay
     (mapply receive queue
             (assoc opts
               :selector (str "JMSCorrelationID='" (.getJMSMessageID message) "'"))))))

(defn respond
  "Listen for messages on queue sent by the request function and
   respond with the result of applying f to the message. queue can
   either be the name of the queue or a javax.jms.Queue. Accepts the
   same options as listen."
  [queue f & {:keys [decode?] :or {decode? true} :as opts}]
  {:pre [(queue? queue)]}
  (letfn [(respond* [^javax.jms.Message msg]
            (publish (.getJMSDestination msg)
                     (f (codecs/decode-if decode? msg))
                     :correlation-id (.getJMSMessageID msg)
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
    (if (instance? java.io.Closeable listener)
      (.close listener)
      (let [complete (promise)]
        (.remove listener #(deliver complete %))
        (when-not (= "removed" (deref complete 5000 nil))
          (log/error "Failed to remove listener" listener))))))

(defn stop
  "Destroy a message destination. Typically not necessary since it
   will be done for you when your app is undeployed. This will fail
   with a warning if any handlers are listening or any messages are
   yet to be delivered. Returns true on success"
  [name & {:keys [force]}]
  (cond
   (queue-name? name) (stop-queue name :force force)
   (topic-name? name) (stop-topic name :force force)
   :else (throw (Exception. "Illegal destination name"))))
