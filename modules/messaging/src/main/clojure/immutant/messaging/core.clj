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

(ns ^{:no-doc true} immutant.messaging.core
  "Internal utilities used by messaging. You should only need to dip
   into here in advanced cases."
  (:use [immutant.util :only (at-exit in-immutant? backoff validate-options)]
        immutant.messaging.internal)
  (:require [immutant.registry          :as registry]
            [immutant.messaging.hornetq :as hornetq]
            [immutant.xa.transaction    :as tx]
            [immutant.logging           :as log])
  (:import (javax.jms DeliveryMode Destination JMSException Queue Session Topic)))

;;; The name of the JBoss connection factory
(def factory-name "jboss.naming.context.java.ConnectionFactory")
;;; Default subscriber name, which can't be null, apparently
(def default-subscriber-name "default")
;;; Options relevant to a connection
(def connect-options [:host :port :username :password :client-id :xa])

;;; Options in effect for the current active messenger
(def ^{:private true, :dynamic true} *options* {})

(defn options
  "Merge an options hash with the ones in effect for this thread"
  [opts & [conn]]
  (let [extra (if conn {:connection conn :sessions nil})]
    (merge *options* opts extra)))

(defn ^{:private true} remote-connection? [opts]
  (:host opts))

(def ^:private local-connection-factory
  (memoize
   (fn []
     (if-let [reference-factory (registry/get factory-name)]
       (let [reference (.getReference reference-factory)]
         (try
           (.getInstance reference)
           (finally (at-exit #(.release reference)))))
       (do
         (log/warn "Unable to obtain JMS Connection Factory - assuming we are outside the container")
         (hornetq/connection-factory))))))

(defn connection-factory
                   [opts]
                   (if (remote-connection? opts)
                     (hornetq/connection-factory opts)
                     (local-connection-factory)))

(defn destination-name [name-or-dest]
  (condp instance? name-or-dest
    Queue (.getQueueName name-or-dest)
    Topic (.getTopicName name-or-dest)
    (str name-or-dest)))

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
    (case attr
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
  (log/info (format "Stopping %s: %s" (if (queue-name? name) "queue" "topic") name))
  (let [izer (registry/get "destinationizer")
        manager (registry/get "jboss.messaging.default.jms.manager")
        removed? (when izer
                   (.destroyDestination izer
                                        (destination-name name)))]
    (if (and (not removed?) manager)
      (try
        (let [result (if (queue-name? name)
                       (.destroyQueue manager (str name))
                       (.destroyTopic manager (str name)))]
          (log/info "Stopped:" name)
          result)
        (catch Throwable e
          (log/warn e)))
      removed?)))

(defn stop-queue [name & {:keys [force]}]
  (if force
    (stop-destination name)
    (if-let [queue (hornetq/destination-controller name)]
      (cond
       (and (.isDurable queue) (< 0 (.getMessageCount queue))) (log/warn "Won't stop non-empty durable queue:" name)
       (< 0 (.getConsumerCount queue)) (throw (IllegalStateException. "Can't stop queue with active consumers"))
       :else (stop-destination name))
      (log/warn "Stop failed - no management interface found for queue:" name))))

(defn stop-topic [name & {:keys [force]}]
  (if force
    (stop-destination name)
    (if-let [topic (hornetq/destination-controller name)]
      (condp > 0
        (.getMessageCount topic) (log/warn "Won't stop topic with messages for durable subscribers:" name)
        (.getSubscriptionCount topic) (throw (IllegalStateException. "Can't stop topic with active subscribers"))
        (stop-destination name))
      (log/warn "Stop failed - no management interface found for topic:" name))))

(defn start-destination [name type f broker-settings]
  (if-let [izer (registry/get "destinationizer")]
    (do
      (log/info (format "Starting %s: %s" type name))
      (let [service-created (f izer)]
        (if-not service-created
          (log/info (format "%s already exists: %s" type name)))
        (hornetq/set-address-options name broker-settings)))
    (throw (Exception. (format "Unable to start %s: %s" type name)))))

(defn ^{:valid-options
        (conj (-> #'hornetq/set-address-options meta :valid-options)
          :durable :selector)}
  start-queue [name & {:keys [durable selector] :or {durable true selector ""} :as opts}]
  (validate-options start-queue opts)
  (start-destination name "queue"
                (fn [izer]
                  (.createQueue izer name durable selector))
                (dissoc opts :durable :selector)))

(defn start-topic [name & {:as opts}]
  (validate-options start-topic hornetq/set-address-options opts)
  (start-destination name "topic"
                     (fn [izer]
                       (.createTopic izer name))
                     opts))

(defprotocol Sessionator
  "Function for obtaining a session"
  (create-session [connection]))

(extend-type javax.jms.XAConnection
  Sessionator
  (create-session [connection]
    (log/debug "Creating XA session")
    (.createXASession connection)))

(extend-type javax.jms.Connection
  Sessionator
  (create-session [connection]
    (log/debug "Creating non-XA session")
    (.createSession connection false Session/AUTO_ACKNOWLEDGE)))

(defn create-connection
  "Creates a connection"
  [opts]
  (log/debug (str "Creating connection " opts))
  (let [conn (backoff
              10 60000
              (if (or (tx/active?) (and (tx/available?) (:xa opts)))
                (.createXAConnection (connection-factory opts)
                                     (:username opts)
                                     (:password opts))
                (.createConnection (connection-factory opts)
                                   (:username opts)
                                   (:password opts))))]
    (if (:client-id opts) (.setClientID conn (:client-id opts)))
    conn))

(defn close-connection
  "Registers a synchronization if there's an active transaction,
   otherwise calls close"
  [conn]
  (if (tx/active?)
    (tx/after-completion #(.close conn))
    (.close conn)))

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
    (set! *options* (update-in *options* [:sessions] assoc transaction session))))

(defn ^javax.jms.XASession session
  []
  (let [transaction (tx/current)]
    (if-not (contains? (:sessions *options*) transaction)
      (enlist-session (create-session (:connection *options*))))
    (get-in *options* [:sessions transaction])))

(defn new-connection
  [opts f]
  (let [conn (create-connection opts)]
    (binding [*options* (options opts conn)]
      (.start conn)
      (try 
        (f)
        (finally
          (close-connection conn))))))

(defn bound-options-match?
  [opts]
  (and (:connection *options*)
       (every? (fn [[k v]] (= v (k *options*)))
               (select-keys opts connect-options))))

(defn with-connection
  ([f]
     (with-connection f {}))
  ([f opts]
     (cond
      (contains? opts :connection) (binding [*options* (options opts (:connection opts))] (f))
      (bound-options-match? opts) (f)
      :else (new-connection opts f))))

(defn ^{:internal true} create-listener [handler]
  (if (in-immutant?)
   (.newMessageListener (registry/get "message-listener-factory") handler)
   (reify javax.jms.MessageListener
     (onMessage [_ message]
       (handler message)))))

(defn ^:internal ^:no-doc delayed
  "Creates an timeout-derefable delay around any function taking a timeout and timeout-val."
  [f]
  (let [val (atom ::unrealized)
        realized? #(not= ::unrealized @val)
        rcv (fn [timeout timeout-val]
              (reset! val (f timeout timeout-val)))]
    (proxy [clojure.lang.Delay clojure.lang.IBlockingDeref] [nil]
      (deref
        ([]
           (if (realized?) @val (rcv 0 nil)))
        ([timeout-ms timeout-val]
           (if (realized?)
             @val
             (rcv timeout-ms timeout-val))))
      (isRealized []
        (realized?)))))

