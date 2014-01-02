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

(ns in-container.test.priority
  (:use clojure.test)
  (:use immutant.messaging))

(def queue "/queue/ham")

(use-fixtures :once (fn [f]
                      (start queue :durable false)
                      (f)
                      (stop queue)))

(deftest default-priority-should-be-fifo
  (let [messages (message-seq queue)]
    (dotimes [x 10] (publish queue x))
    (is (= (range 10) (take 10 messages)))))

(deftest prioritize-by-integer
  (let [messages (message-seq queue)]
    (dotimes [x 10] (publish queue x :priority x))
    (is (= (reverse (range 10)) (take 10 messages)))))

(deftest prioritize-by-keyword
  (let [messages (message-seq queue)
        labels [:low :normal :high :critical]
        size (count labels)]
    (dotimes [x size] (publish queue x :priority (labels x)))
    (is (= (reverse (range size)) (take size messages)))))

(deftest select-lower-priority
  (publish queue 1 :properties {:prop 5} :priority :high)
  (publish queue 2 :properties {:prop 3} :priority :low)
  (is (= 2 (receive queue :selector "prop < 5")))
  (is (= 1 (receive queue)))
  (is (nil? (receive queue :timeout 1000))))
