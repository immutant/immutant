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
  (:use [immutant.messaging.core])
  (:require [immutant.registry :as lookup])
  (:require [immutant.messaging.codecs :as codecs])
  (:require [immutant.messaging.hornetq-direct :as hornetq]))

(defn start [name & opts]
  "Create a message destination; name should be prefixed with either /queue or /topic"
  (when-not (or (queue? name) (topic? name))
    (throw (Exception. "Destination names must start with /queue or /topic")))
  (if (queue? name)
    (apply start-queue (cons name opts))
    (apply start-topic (cons name opts))))

(defn stop [name]
  "Destroy message destination"
  (cond
   (queue? name) (stop-queue name)
   (topic? name) (stop-topic name)
   :else (throw (Exception. "Illegal destination name"))))

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

(defn listen [dest-name f]
  "The handler function, f, will receive any messages sent to dest-name"
  (let [connection (.createConnection connection-factory)]
    (try
      (let [session (create-session connection)
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

(defn wait-for-destination [f & [count]]
  "Ignore exceptions, retrying until destination completely starts up"
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


