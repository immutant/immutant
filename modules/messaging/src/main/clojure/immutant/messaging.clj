;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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
  (:use [immutant.utilities :only (at-exit)])
  (:import (javax.jms Session))
  (:require [immutant.registry :as lookup])
  (:require [immutant.messaging.codecs :as codecs])
  (:require [immutant.messaging.hornetq-direct :as hornetq]))

(declare with-session destination connection-factory)

(defn publish [dest-name message & opts]
  "Send a message to a destination"
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        producer (.createProducer session destination)
                        encoded (codecs/encode session message opts)]
                  (.send producer encoded)))))
    
(defn receive [dest-name & {timeout :timeout}]
  "Receive a message from a destination"
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        consumer (.createConsumer session destination)
                        encoded (.receive consumer (or timeout 10000))]
                    (codecs/decode encoded)))))

(defn processor [dest-name f]
  (let [connection (.createConnection connection-factory)]
    (try
      (let [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)
            destination (destination session dest-name)
            consumer (.createConsumer session destination)]
        (.setMessageListener consumer (proxy [javax.jms.MessageListener] []
                                        (onMessage [message]
                                          (f (codecs/decode message)))))
        (at-exit #(do (.close connection) (println "JC: closed" connection)))
        (.start connection))
      (catch Throwable e
        (.close connection)
        (throw e)))))

(defn stop-queue [name]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (.destroyQueue manager name)))
  
(defn start-queue [name & {:keys [durable selector] :or {durable false selector ""}}]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (and (.createQueue manager false name selector durable (into-array String []))
         (at-exit #(do (stop-queue name) (println "JC: stopped queue" name))))
    (println "WARN: unable to start queue," name)))

(defn stop-topic [name]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (.destroyTopic manager name)))
  
(defn start-topic [name & opts]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (and (.createTopic manager false name (into-array String []))
         (at-exit #(do (stop-topic name) (println "JC: stopped topic" name))))
    (println "WARN: unable to start topic," name)))

(defn wait-for-destination [f & [count]]
  "Ignore exceptions, retrying until destination starts up"
  (let [attempts (or count 30)
        retry #(do (Thread/sleep 1000) (wait-for-destination f (dec attempts)))]
    (try
      (f)
      (catch RuntimeException e
        (if (and (instance? javax.jms.JMSException (.getCause e)) (> attempts 0))
          (retry)
          (throw e)))
      (catch javax.naming.NameNotFoundException e
        (if (> attempts 0) (retry) (throw e)))
      (catch javax.jms.JMSException e
        (if (> attempts 0) (retry) (throw e))))))

;; privates

(def ^:private connection-factory
  (if-let [reference-factory (lookup/service "jboss.naming.context.java.ConnectionFactory")]
    (let [reference (.getReference reference-factory)]
      (try
        (.getInstance reference)
        (finally (at-exit #(do (.release reference) (println "JC: released" reference))))))
    (do
      (println "WARN: unable to obtain JMS Connection Factory so we must be outside container")
      hornetq/connection-factory)))

(defn- with-session [f]
  (with-open [connection (.createConnection connection-factory)
              session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
    (.start connection)
    (f session)))

(defn- destination [session name]
  (if (.contains name "queue")
    (.createQueue session name)
    (.createTopic session name)))

