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

(ns test.immutant.messaging.core
  (:use [immutant.messaging.core]
        [clojure.test])
  (:import [javax.jms DeliveryMode])
  (:require [immutant.messaging :as main]))

(def producer (proxy [javax.jms.MessageProducer] []
                (getDeliveryMode [] DeliveryMode/PERSISTENT)
                (getPriority [] (Integer. 4))
                (getTimeToLive [] (Long. 99999))))

(deftest options-missing
  (let [opts (wash-publish-options [] producer)]
    (is (= (:delivery opts) DeliveryMode/PERSISTENT))
    (is (= (:priority opts) 4))
    (is (= (:ttl opts) 99999))))

(deftest option-persistent-false
  (let [opts (wash-publish-options {:persistent false} producer)]
    (is (= (:delivery opts) DeliveryMode/NON_PERSISTENT))))

(deftest option-persistent-true
  (let [opts (wash-publish-options {:persistent true} producer)]
    (is (= (:delivery opts) DeliveryMode/PERSISTENT))))

(deftest option-priority-keyword
  (let [opts (wash-publish-options {:priority :high} producer)]
    (is (= (:priority opts) 7))))

(deftest option-priority-integer
  (let [opts (wash-publish-options {:priority 8} producer)]
    (is (= (:priority opts) 8))))

(deftest option-ttl-long
  (let [opts (wash-publish-options {:ttl 1000} producer)]
    (is (= (:ttl opts) 1000))))

(deftest with-connection-options
  (main/with-connection {:host 42 :connection :unused}
    (is (= 42 (:host (options {:not :here}))))
    (is (= 69 (:host (options {:host 69}))))
    (is (contains? (options {}) :sessions))
    (is (nil? (:sessions (options {}))))
    (is (= :unused (:connection (options {}))))))

;; Tests/utilities for message property setting

(defmacro message-proxy
  "Create proxy that only responds to a single setter"
  [setter]
  `(let [params# (atom {})]
     (proxy [javax.jms.Message][]
       (~setter [k# v#] (swap! params# assoc k# v#))
       (getObjectProperty [key#] (@params# key#)))))

(defn test-properties [message properties pred]
  (set-properties! message properties)
  (is (every? pred (map #(.getObjectProperty message (name %)) (keys properties)))))
  
(deftest properties-long
  (test-properties (message-proxy setLongProperty)
                   {:literal 6
                    :int     (int 6)
                    :long    (long 6)
                    :bigint  (bigint 6)
                    :short   (short 6)
                    :byte    (byte 6)}
                   (partial = 6)))

(deftest properties-double
  (test-properties (message-proxy setDoubleProperty)
                   {:literal 6.5
                    :float   (float 6.5)
                    :double  (double 6.5)}
                   (partial = 6.5)))

(deftest properties-boolean
  (test-properties (message-proxy setBooleanProperty)
                   {:literal true
                    :boolean (boolean 6)}
                   (partial = true))
  (test-properties (message-proxy setBooleanProperty)
                   {:literal false
                    :boolean (boolean nil)}
                   (partial = false)))

(deftest properties-string
  (test-properties (message-proxy setStringProperty)
                   {:literal "{}"
                    :hashmap {}}
                   (partial = "{}")))
