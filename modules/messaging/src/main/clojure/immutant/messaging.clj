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
  (:import (javax.jms Session))
  (:require [immutant.messaging.codecs :as codecs])
  (:require [immutant.messaging.hornetq-direct :as hornetq]))

(declare produce consume)

(defn publish [destination message & opts]
  "Send a message to a destination"
  (produce destination message opts))
    
(defn receive [destination & opts]
  "Receive a message from a destination"
  (consume destination opts))

(defn wait-for-destination [f & count]
  (let [attempts (or count 30)
        retry #(do (Thread/sleep 1000) (wait-for-destination f (dec attempts)))]
    (try
      (f)
      (catch javax.naming.NameNotFoundException e
        (if (> attempts 0) (retry) (throw e)))
      (catch javax.jms.JMSException e
        (if (> attempts 0) (retry) (throw e))))))

;; privates

(def connection-factory hornetq/connection-factory)

(defn- with-session [f]
  (with-open [connection (.createConnection connection-factory)
              session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
    (.start connection)
    (f session)))

(defn- java-destination [destination]
  (hornetq/java-destination destination))

(defn- produce [destination message opts]
  (with-session (fn [session]
                  (.send
                   (.createProducer session (java-destination destination))
                   (codecs/encode session message opts)))))

(defn- consume [destination {timeout :timeout}]
  (with-session (fn [session]
                  (codecs/decode
                   (.receive (.createConsumer
                              session
                              (java-destination destination)) (or timeout 10000))))))


