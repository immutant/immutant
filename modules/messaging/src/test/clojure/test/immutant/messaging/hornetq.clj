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

(ns test.immutant.messaging.hornetq
  (:use immutant.messaging.hornetq
        clojure.test)
  (:require [immutant.messaging :as msg]))

(defprotocol IConnectionFactory
  (setRetryInterval [this num])
  (setRetryIntervalMultiplier [this num])
  (setReconnectAttempts [this num])
  (setMaxRetryInterval [this num]))

(defrecord MockFactory [state] IConnectionFactory
  (setRetryInterval [this num] (swap! state assoc :retry-interval num))
  (setRetryIntervalMultiplier [this num] (swap! state assoc :retry-interval-multiplier num))
  (setReconnectAttempts [this num] (swap! state assoc :reconnect-attempts num))
  (setMaxRetryInterval [this num] (swap! state assoc :max-retry-interval num)))

(deftest retry-options []
  (let [mf (MockFactory. (atom {}))
        returned-fac (set-retry-options mf {:retry-interval 1
                                            :retry-interval-multiplier 2
                                            :reconnect-attempts 3
                                            :max-retry-interval 4})
        returned-state @(.state returned-fac)]
    (is (= (:retry-interval returned-state) 1))
    (is (= (:retry-interval-multiplier returned-state) 2))
    (is (= (:reconnect-attempts returned-state) 3))
    (is (= (:max-retry-interval returned-state) 4))))

(deftest normalize-destination-match-works
  (are [exp given] (= exp (#'immutant.messaging.hornetq/normalize-destination-match given))
       "#"                   "#"
       "jms.queue.#"         "jms.queue.#"
       "jms.queue.foo.queue" "foo.queue"
       "jms.queue.queue.*"   "queue.*"
       "jms.queue.#"         (msg/as-queue "#"))
  (doseq [match ["jms.#" "*" "foo"]]
    (is (thrown? IllegalArgumentException
          (#'immutant.messaging.hornetq/normalize-destination-match match)))))
