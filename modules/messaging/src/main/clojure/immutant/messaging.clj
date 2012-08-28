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
  (:use [immutant.utilities :only (at-exit mapply)]
        [immutant.messaging.core])
  (:require [immutant.messaging.codecs :as codecs]
            [immutant.registry         :as reg]))

(defn start
  "Create a message destination; name should be prefixed with either /queue or /topic"
  [name & opts]
  (cond
   (queue-name? name) (apply start-queue name opts)
   (topic-name? name) (apply start-topic name opts)
   :else         (throw (Exception. "Destination names must start with /queue or /topic"))))

(defn publish
  "Send a message to a destination. dest can either be the name of the
destination or a javax.jms.Destination. If the message is a javax.jms.Message,
then the message is sent without modification to dest. Returns the JMS message
object that was published.

The following options are supported [default]:
  :encoding        :clojure :json or :text [:clojure]
  :priority        0-9 or :low :normal :high :critical [4]
  :ttl             time to live, in ms [0=forever]
  :persistent      whether undelivered messages survive restarts [true]
  :properties      a hash to which selectors may be applied [nil]
  :correlation-id  used to set the JMSCorrelationID (see
                   http://docs.oracle.com/javaee/6/api/javax/jms/Message.html#setJMSCorrelationID(java.lang.String) [nil]
  :host           the remote host to connect to (default is to connect in-vm)
                  [nil]
  :port           the remote port to connect to (requires :host to be set)
                  [nil, or 5445 if :host is set]
  :username       the username to use to auth the connection (requires :password
                  to be set) [nil]
  :password       the password to use to auth the connection (requires :username
                  to be set) [nil]"
  [dest message & {:as opts}]
  (with-connection opts
    (let [session (session)
          destination (destination session dest)
          producer (.createProducer session destination)
          encoded (if (instance? javax.jms.Message message)
                    message
                    (-> (codecs/encode session message opts)
                        (set-properties! (:properties opts))
                        (set-attributes! opts)))
          {:keys [delivery priority ttl]} (wash-publish-options opts producer)]
      (.send producer encoded delivery priority ttl)
      encoded)))

(defn receive
  "Receive a message from a destination. dest can either be the name of the
destination or a javax.jms.Destination.

The following options are supported [default]:
  :timeout    time in ms, after which nil is returned [10000]
  :selector   A JMS (SQL 92) expression matching message properties
  :decode?    if true, the decoded message body is returned. Otherwise, the
              javax.jms.Message object is returned [true]
  :host       the remote host to connect to (default is to connect in-vm) [nil]
  :port       the remote port to connect to (requires :host to be set) [nil, or
              5445 if :host is set]
  :username   the username to use to auth the connection (requires :password to
              be set) [nil]
  :password   the password to use to auth the connection (requires :username to
              be set) [nil]"
  [dest & {:keys [timeout selector decode?] :or {timeout 10000 decode? true} :as opts}]
  (with-connection opts
    (let [session (session)
          destination (destination session dest)
          consumer (.createConsumer session destination selector)
          encoded (.receive consumer timeout)]
      (when encoded
        (codecs/decode-if decode? encoded)))))

(defn message-seq
  "A lazy sequence of messages received from a destination. Accepts same options as receive"
  [dest & opts]
  (lazy-seq (cons (apply receive dest opts) (message-seq dest))))

(defn listen
  "The handler function, f, will receive each message sent to dest. dest can
either be the name of the destination or a javax.jms.Destination.

The following options are supported [default]:
  :concurrency  the number of threads handling messages [1]
  :selector     A JMS (SQL 92) expression matching message properties
  :decode?      if true, the decoded message body is passed to f. Otherwise, the
                javax.jms.Message object is passed [true]
  :host         the remote host to connect to (default is to connect in-vm) [nil]
  :port         the remote port to connect to (requires :host to be set) [nil,
                or 5445 if :host is set]
  :username     the username to use to auth the connection (requires :password
                to be set) [nil]
  :password     the password to use to auth the connection (requires :username
                to be set) [nil]"
  [dest f & {:keys [concurrency selector decode?] :or {concurrency 1 decode? true} :as opts}]
  (let [connection (.createXAConnection (connection-factory opts)
                                        (:username opts)
                                        (:password opts))]
    (try
      (dotimes [_ concurrency]
        (let [^javax.jms.Session session (create-session connection)
              destination (destination session dest)
              consumer (.createConsumer session destination selector)
              handler #(with-transaction session
                         (f (codecs/decode-if decode? %)))]
          (.setMessageListener consumer
                               (if-let [runtime (reg/fetch "clojure-runtime")]
                                 (org.immutant.messaging.MessageListener. runtime handler)
                                 (reify javax.jms.MessageListener
                                   (onMessage [_ message]
                                     (handler message)))))))
      (at-exit #(.close connection))
      (.start connection)
      connection
      (catch Throwable e
        (.close connection)
        (throw e)))))

(defn request
  "Send a message to queue and return a delay that will retrieve the response.
Implements the request-response pattern, and is used in conjunction with respond.
queue can either be the name of the queue or a javax.jms.Queue.
In addition to the same options as publish, it also accepts [default]:
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
  "Listen for messages on queue sent by the request function and respond with the
result of applying f to the message. queue can either be the name of the
queue or a javax.jms.Queue. Accepts the same options as listen."
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

(defn unlisten
  "Pass the result of a call to listen or respond to de-register the handler.
You only need to do this if you wish to stop the handler's destination before
your app is undeployed."
  [^javax.jms.XAConnection listener]
  (.close listener))

(defn stop
  "Destroy a message destination. Typically not necessary since it
  will be done for you when your app is undeployed. This will fail
  with a warning if any handlers are listening"
  [name]
  (if (or (queue-name? name) (topic-name? name))
    (stop-destination name)
    (throw (Exception. "Illegal destination name"))))
