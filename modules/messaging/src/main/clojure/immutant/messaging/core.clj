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

(ns immutant.messaging.core
  (:use [immutant.utilities :only (at-exit)])
  (:import (javax.jms DeliveryMode Destination Queue Session Topic))
  (:require [immutant.registry          :as lookup]
            [immutant.messaging.hornetq :as hornetq]
            [immutant.xa.transaction    :as tx]
            [clojure.tools.logging      :as log]))

;;; The name of the JBoss connection factory
(def factory-name "jboss.naming.context.java.ConnectionFactory")

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

;; ignore reflection here since it only occurs once at compile time
(let [local-connection-factory
      (if-let [reference-factory (lookup/fetch factory-name)]
        (let [reference (.getReference reference-factory)]
          (try
            (.getInstance reference)
            (finally (at-exit #(.release reference)))))
        (do
          (log/warn "Unable to obtain JMS Connection Factory - assuming we are outside the container")
          (hornetq/connection-factory)))]
  (defn ^org.hornetq.jms.client.HornetQConnectionFactory connection-factory
    [opts]
    (if (remote-connection? opts)
      (hornetq/connection-factory opts)
      local-connection-factory)))


(defn queue-name? [^String name]
  (.startsWith name "/queue"))

(defn queue? [queue]
  (or (isa? (class queue) Queue) (queue-name? queue)))

(defn topic-name? [^String name]
  (.startsWith name "/topic"))

(defn topic? [topic]
  (or (isa? (class topic) Topic) (topic-name? topic)))

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

(defn destination [^Session session name-or-dest]
  (cond
   (isa? (class name-or-dest) Destination) name-or-dest
   (queue-name? name-or-dest) (.createQueue session name-or-dest)
   (topic-name? name-or-dest) (.createTopic session name-or-dest)
   :else (throw (Exception. "Illegal destination name"))))

(defn stop-destination [name]
  (if-let [^org.hornetq.jms.server.JMSServerManager manager
           (lookup/fetch "jboss.messaging.default.jms.manager")]
    (try
      (if (queue-name? name)
        (.destroyQueue manager name)
        (.destroyTopic manager name))
      (log/info "Stopped" name)
      (catch Throwable e
        (log/warn e)))))

(defn start-queue [name & {:keys [durable selector] :or {durable false selector ""}}]
  (if-let [^org.hornetq.jms.server.JMSServerManager manager
           (lookup/fetch "jboss.messaging.default.jms.manager")]
    (do (.createQueue manager false name selector durable (into-array String []))
        (at-exit #(stop-destination name)))
    (throw (Exception. (str "Unable to start queue, " name)))))

(defn start-topic [name & opts]
  (if-let [^org.hornetq.jms.server.JMSServerManager manager
           (lookup/fetch "jboss.messaging.default.jms.manager")]
    (do (.createTopic manager false name (into-array String []))
        (at-exit #(stop-destination name)))
    (throw (Exception. (str "Unable to start topic, " name)))))

(defn create-session [^javax.jms.XAConnection connection]
  (if (lookup/fetch factory-name)
    (.createXASession connection)
    (.createSession connection false Session/AUTO_ACKNOWLEDGE)))

(defn create-connection
  "Creates a connection and registers it in the *connections* map"
  [opts]
  (let [conn (.createXAConnection (connection-factory opts) (:username opts) (:password opts))]
    (set! *connections* (assoc *connections* (connection-key opts) conn))
    conn))

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

;; TODO: This is currently unused and, if deemed necessary, could
;; probably be better implemented
(defn wait-for-destination 
  "Ignore exceptions, retrying until destination completely starts up"
  [f & [count]]
  (let [attempts (or count 30)
        retry #(do (Thread/sleep 1000) (wait-for-destination f (dec attempts)))]
    (try
      (f)
      (catch javax.jms.JMSException e            ;; clojure 1.2, 1.4
        (if (> attempts 0) (retry) (throw e)))
      (catch RuntimeException e                  ;; clojure 1.3
        (if (and (instance? javax.jms.JMSException (.getCause e)) (> attempts 0))
          (retry)
          (throw e)))
      (catch javax.naming.NameNotFoundException e
        (if (> attempts 0) (retry) (throw e))))))
