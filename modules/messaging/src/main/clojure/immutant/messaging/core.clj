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
  (:import (javax.jms Session DeliveryMode))
  (:require [immutant.registry :as lookup])
  (:require [immutant.messaging.hornetq :as hornetq]))

(def connection-factory
  (if-let [reference-factory (lookup/fetch "jboss.naming.context.java.ConnectionFactory")]
    (let [reference (.getReference reference-factory)]
      (try
        (.getInstance reference)
        (finally (at-exit #(.release reference)))))
    (do
      (println "WARN: unable to obtain JMS Connection Factory so we must be outside container")
      (hornetq/connection-factory))))

(defn queue? [name]
  (.startsWith name "/queue"))

(defn topic? [name]
  (.startsWith name "/topic"))

(defn wash-publish-options
  "Wash publish options relative to default values from a producer"
  [opts producer]
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

(defn set-properties!
  "Set user-defined properties on a JMS message. Returns message"
  [message properties]
  (doseq [[k,v] properties]
    (let [key (name k)]
      (cond
       (integer? v) (.setLongProperty message key (long v))
       (float? v) (.setDoubleProperty message key (double v))
       (instance? Boolean v) (.setBooleanProperty message key v)
       :else (.setStringProperty message key (str v)))))
  message)

(defn create-session [connection]
  (.createSession connection false Session/AUTO_ACKNOWLEDGE))

(defn with-session [f]
  (with-open [connection (.createConnection connection-factory)
              session (create-session connection)]
    (.start connection)
    (f session)))

(defn destination [session name]
  (cond
   (queue? name) (.createQueue session name)
   (topic? name) (.createTopic session name)
   :else (throw (Exception. "Illegal destination name"))))

(defn stop-destination [name]
  (if-let [manager (lookup/fetch "jboss.messaging.default.jms.manager")]
    (try
      (if (queue? name)
        (.destroyQueue manager name)
        (.destroyTopic manager name))
      (println "Stopped" name)
      (catch Throwable e
        (println "WARN:" (.getMessage (.getCause e)))))))

(defn start-queue [name & {:keys [durable selector] :or {durable false selector ""}}]
  (if-let [manager (lookup/fetch "jboss.messaging.default.jms.manager")]
    (do (.createQueue manager false name selector durable (into-array String []))
        (at-exit #(stop-destination name)))
    (throw (Exception. (str "Unable to start queue, " name)))))

(defn start-topic [name & opts]
  (if-let [manager (lookup/fetch "jboss.messaging.default.jms.manager")]
    (do (.createTopic manager false name (into-array String []))
        (at-exit #(stop-destination name)))
    (throw (Exception. (str "Unable to start topic, " name)))))


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
