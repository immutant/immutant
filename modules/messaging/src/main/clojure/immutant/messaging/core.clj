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

(ns ^{:no-doc true} immutant.messaging.core
  "Internal utilities used by messaging. You should only need to dip
   into here in advanced cases."
  (:use [immutant.util :only (at-exit if-in-immutant)])
  (:require [immutant.registry          :as registry]
            [immutant.messaging.hornetq :as hornetq]
            [immutant.xa.transaction    :as tx]
            [clojure.tools.logging      :as log])
  (:import (javax.jms DeliveryMode Destination JMSException Queue Session Topic)))

;;; The name of the JBoss connection factory
(def factory-name "jboss.naming.context.java.ConnectionFactory")
;;; Default subscriber name, which can't be null, apparently
(def default-subscriber-name "default")


;;; Thread-local connection set
(def ^{:private true, :dynamic true} *connections* nil)
;;; Thread-local current connection
(def ^{:private true, :dynamic true} ^javax.jms.XAConnection *connection* nil)
;;; Thread-local map of transactions to sessions
(def ^{:private true, :dynamic true} *sessions* nil)

(defn ^{:private true} remote-connection? [opts]
  (:host opts))

(defn ^{:private true} connection-key [opts]
  (map #(% opts) [:host :port :username :password]))

(let [local-connection-factory
      (if-let [reference-factory (registry/get factory-name)]
        (let [reference (.getReference reference-factory)]
          (try
            (.getInstance reference)
            (finally (at-exit #(.release reference)))))
        (do
          (log/warn "Unable to obtain JMS Connection Factory - assuming we are outside the container")
          (hornetq/connection-factory)))]
  (defn connection-factory
    [opts]
    (if (remote-connection? opts)
      (hornetq/connection-factory opts)
      local-connection-factory)))

(defrecord QueueMarker [name]
  Object
  (toString [_] name))

(defrecord TopicMarker [name]
  Object
  (toString [_] name))

(defn destination-name-error [name]
  (IllegalArgumentException.
   (str \' name \'
        " is ambiguous. Destination names must contain 'queue' or 'topic',"
        " or be wrapped in a call to as-queue or as-topic.")))

(defn queue-name? [name]
  (or (instance? QueueMarker name)
      (and (instance? String name) 
           (.contains name "queue"))))

(defn queue? [queue]
  (or (instance? Queue queue)
      (queue-name? queue)))

(defn topic-name? [name]
  (or (instance? TopicMarker name)
      (and (instance? String name) 
           (.contains name "topic"))))

(defn topic? [topic]
  (or (instance? Topic topic)
      (topic-name? topic)))

(defn destination-name [name-or-dest]
  (condp instance? name-or-dest
    javax.jms.Queue (.getQueueName name-or-dest)
    javax.jms.Topic (.getTopicName name-or-dest)
    (str name-or-dest)))

(defn jms-name [dest-name]
  (str "jms." (if (queue-name? dest-name) "queue." "topic.") dest-name))

(defn wash-publish-options
  "Wash publish options relative to default values from a producer"
  [opts ^javax.jms.MessageProducer producer]
  {:delivery (if (contains? opts :persistent)
               (if (:persistent opts)
                 DeliveryMode/PERSISTENT
                 DeliveryMode/NON_PERSISTENT)
               (.getDeliveryMode producer))
   :priority (let [priorities {:low 1, :normal 4, :high 7, :critical 9}]
               (or (priorities (:priority opts))
                   (:priority opts)
                   (.getPriority producer)))
   :ttl (or (:ttl opts) (.getTimeToLive producer))})

(defn set-attributes!
  "Sets attributes on a JMS message. Returns message."
  [^javax.jms.Message message attributes]
  (doseq [[attr v] attributes]
    (condp = attr
      :correlation-id (.setJMSCorrelationID message v)
      :reply-to       (.setJMSReplyTo message v)
      :type           (.setJMSType message v)
      nil))
  message)

(defn set-properties!
  "Set user-defined properties on a JMS message. Returns message"
  [^javax.jms.Message message properties]
  (doseq [[k,v] properties]
    (let [key (name k)]
      (cond
       (integer? v) (.setLongProperty message key (long v))
       (float? v) (.setDoubleProperty message key (double v))
       (instance? Boolean v) (.setBooleanProperty message key v)
       :else (.setStringProperty message key (str v)))))
  message)

(defn get-properties
  "Extract properties from message into a map, turning the JMS
   property names into keywords unless the :keywords option is false"
  [message & {:keys [keywords] :or {keywords true}}]
  (into {} (for [k (enumeration-seq (.getPropertyNames message))]
             [(if keywords (keyword k) k) (.getObjectProperty message k)])))

(defn create-destination [^Session session dest]
  (cond
   (instance? Destination dest) dest
   (queue-name? dest)           (.createQueue session (str dest))
   (topic-name? dest)           (.createTopic session (str dest))
   :else (throw (destination-name-error dest))))

(defn stop-destination [name]
  (let [izer (registry/get "destinationizer")
        manager (registry/get "jboss.messaging.default.jms.manager")
        removed? (when izer
                   (let [complete (promise)]
                     (and (.destroyDestination izer
                                               (destination-name name)
                                               #(deliver complete %))
                          (= "removed" (deref complete 5000 nil)))))]
    (if (and (not removed?) manager)
      (try
        (let [result (if (queue-name? name)
                       (.destroyQueue manager (str name))
                       (.destroyTopic manager (str name)))]
          (log/info "Stopped" name)
          result)
        (catch Throwable e
          (log/warn e)))
      removed?)))

(defn stop-queue [name & {:keys [force]}]
  (if force
    (stop-destination name)
    (if-let [default (registry/get "jboss.messaging.default")]
      (if-let [queue (.getResource (.getManagementService default) (jms-name name))]
        (cond
         (and (.isDurable queue) (< 0 (.getMessageCount queue))) (log/warn "Won't stop non-empty durable queue:" name)
         (< 0 (.getConsumerCount queue)) (throw (IllegalStateException. "Can't stop queue with active consumers"))
         :else (stop-destination name))
        (log/warn "Stop failed - no management interface found for queue:" name)))))

(defn stop-topic [name & {:keys [force]}]
  (if force
    (stop-destination name)
    (if-let [default (registry/get "jboss.messaging.default")]
      (if-let [topic (.getResource (.getManagementService default) (jms-name name))]
        (condp > 0
          (.getMessageCount topic) (log/warn "Won't stop topic with messages for durable subscribers:" name)
          (.getSubscriptionCount topic) (throw (IllegalStateException. "Can't stop topic with active subscribers"))
          (stop-destination name))
        (log/warn "Stop failed - no management interface found for topic:" name)))))

(defn start-queue [name & {:keys [durable selector] :or {durable true selector ""}}]
  (if-let [izer (registry/get "destinationizer")]
    (let [complete (promise)
          service-created (.createQueue izer name durable selector #(deliver complete %))]
      (if service-created
        (when (not= "up" (deref complete 5000 nil))
          (throw (Exception. (str "Unable to start queue: " name))))
        (log/info "Queue already exists:" name)))
    (throw (Exception. (str "Unable to start queue: " name)))))

(defn start-topic [name & opts]
  (if-let [izer (registry/get "destinationizer")]
    (let [complete (promise)
          service-created (.createTopic izer name #(deliver complete %))]
      (if service-created
        (when (not= "up" (deref complete 5000 nil))
          (throw (Exception. (str "Unable to start topic: " name))))
        (log/info "Topic already exists:" name)))
    (throw (Exception. (str "Unable to start topic: " name)))))

(defn create-session [^javax.jms.XAConnection connection]
  (if (registry/get factory-name)
    (.createXASession connection)
    (.createSession connection false Session/AUTO_ACKNOWLEDGE)))

(defn create-connection
  "Creates a connection and registers it in the *connections* map"
  [opts]
  (let [conn (.createXAConnection (connection-factory opts) (:username opts) (:password opts))]
    (if (:client-id opts) (.setClientID conn (:client-id opts)))
    (if *connections* (set! *connections* (assoc *connections* (connection-key opts) conn)))
    conn))

(defn create-consumer
  "Creates a consumer for a session and destination that may be a durable topic subscriber"
  [session destination {:keys [selector client-id subscriber-name] :or {subscriber-name default-subscriber-name}}]
  (if (and client-id (instance? javax.jms.Topic destination))
    (.createDurableSubscriber session destination subscriber-name selector false)
    (.createConsumer session destination selector)))

(defn destination-exists?
  [connection name-or-dest]
  (try
    (with-open [session (create-session connection)]
      (create-destination session name-or-dest))
    (catch JMSException _)))

(defn enlist-session
  "Enlist a session in the current transaction, if any"
  [^javax.jms.XASession session]
  (let [transaction (tx/current)]
    (if transaction
      (tx/enlist (.getXAResource session)))
    (set! *sessions* (assoc *sessions* transaction session))))

(defn ^javax.jms.XASession session
  []
  (let [transaction (tx/current)]
    (if-not (contains? *sessions* transaction)
      (enlist-session (create-session *connection*)))	  	
    (get *sessions* transaction)))

(defmacro with-transaction
  [session & body]
  `(if (tx/available?)
     (binding [*sessions* {}]
       (tx/requires-new
        (enlist-session ~session)
        ~@body))
     (do
       ~@body)))
  
(defn with-connection* [opts f]
  (binding [*connections* (or *connections* {})]
    (if-let [conn (*connections* (connection-key opts))]
      ;; this connection has been used before in the current call stack, just rebind it
      (binding [*connection* conn] 
        (f))
      ;; we need a new connection, so we have to start it and clean up after
      (binding [*connection* (create-connection opts)
                *sessions* {}]
        (.start *connection*)
        (try 
          (f)
          (finally
           (let [conn *connection*]
             (if (tx/active?)
               (tx/after-completion #(.close conn))
               (.close conn)))))))))

(defmacro with-connection [options & body]
  `(with-connection* ~options (fn [] ~@body)))

(defn ^{:internal true} create-listener [handler]
  (if-in-immutant
   (.newMessageListener (registry/get "message-listener-factory") handler)
   (reify javax.jms.MessageListener
     (onMessage [_ message]
       (handler message)))))
