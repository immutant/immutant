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

(ns immutant.integs.msg.selector
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def ham-queue "/queue/ham")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/selector"
                       }))

(deftest select-lower-priority
  (publish ham-queue 1 :properties {:prop 5} :priority :high)
  (publish ham-queue 2 :properties {:prop 3} :priority :low)
  (is (= 2 (receive ham-queue :selector "prop < 5")))
  (is (= 1 (receive ham-queue))))

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
               (publish ham-queue value :properties {:prop value})
               (= value (receive ham-queue :selector selector)))]
    ;; First put a message in there that should be ignored by all selectors
    (publish ham-queue "first")
    ;; Run through all the selectors, ensuring their corresponding values are received
    (is (every? identity (map func selectors values)))
    ;; Now purge the ignored message
    (is (= "first" (receive ham-queue)))))
    
(deftest selectors-on-queues-and-listeners
  (publish "/queue/filtered" "failure")
  (is (nil? (receive "/queue/filtered" :timeout 1000)))
  (publish "/queue/filtered" "success" :properties {:color "blue"})
  (is (= "success" (receive "/queue/filtered")))
  (is (nil? (receive "/queue/ham" :timeout 1000)))
  (publish "/queue/filtered" "success" :properties {:color "blue" :animal "penguin"})
  (is (= "success" (receive "/queue/ham"))))
