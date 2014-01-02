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

(ns immutant.integs.msg.selector
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [remote]])
  (:require [immutant.messaging :as msg]))

(def publish (partial remote msg/publish))

(def receive (partial remote msg/receive))

(def queue "/queue/selectors")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/selector"
                       }))

(deftest select-lower-priority
  (publish queue 1 :properties {:prop 5} :priority :high)
  (publish queue 2 :properties {:prop 3} :priority :low)
  (is (= 2 (receive queue :selector "prop < 5")))
  (is (= 1 (receive queue)))
  (is (nil? (receive queue :timeout 1000))))

(deftest various-selectors
  (let [selectors ["prop = true"
                   "prop is not null and prop <> false"
                   "prop = 5"
                   "prop > 4"
                   "prop = 5.5"
                   "prop < 6"
                   "prop = 'string'"]
        values [true
                true
                5
                5
                5.5
                5.5
                "string"]
        func (fn [selector, value]
               (publish queue value :properties {:prop value})
               (= value (receive queue :selector selector)))]
    ;; First put a message in there that should be ignored by all selectors
    (publish queue "first")
    ;; Run through all the selectors, ensuring their corresponding values are received
    (is (every? identity (map func selectors values)))
    ;; Now purge the ignored message
    (is (= "first" (receive queue)))))
    
(deftest selectors-on-queues-and-listeners
  (publish "/queue/filtered" "failure")
  (is (nil? (receive "/queue/filtered" :timeout 1000)))
  (publish "/queue/filtered" "success" :properties {:color "blue"})
  (is (= "success" (receive "/queue/filtered")))
  (is (nil? (receive queue :timeout 1000)))
  (publish "/queue/filtered" "success" :properties {:color "blue" :animal "penguin"})
  (is (= "success" (receive queue))))
