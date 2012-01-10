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

(ns test.immutant.messaging.core
  (:use [immutant.messaging.core])
  (:import (javax.jms DeliveryMode))
  (:use [clojure.test]))

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
