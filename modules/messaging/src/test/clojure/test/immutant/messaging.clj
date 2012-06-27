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

(ns test.immutant.messaging
  (:use immutant.messaging
        clojure.test)
  (:require [immutant.registry :as lookup]))

(def dummies { "jboss.messaging.default.jms.manager"
               (proxy [org.hornetq.jms.server.JMSServerManager][]
                 (createQueue [& _] false)
                 (createTopic [& _] false))
               "housekeeper" nil})

(defn test-already-running [destination]
  (let [names (atom (keys dummies))]
    (with-redefs [lookup/fetch (fn [k]
                                 (swap! names (partial remove #(= % k)))
                                 (dummies k))]
      (is (not (empty? @names)))
      (is (= nil (start destination)))
      (is (empty? @names)))))

(deftest start-queue-outside-of-container
  (is (thrown-with-msg? Exception #"Unable to start queue" (start "/queue/barf"))))

(deftest start-topic-outside-of-container
  (is (thrown-with-msg? Exception #"Unable to start topic" (start "/topic/barf"))))

(deftest queue-already-running
  (test-already-running "/queue/foo"))

(deftest topic-already-running
  (test-already-running "/topic/foo"))
  
(deftest bad-destination-name
  (is (thrown-with-msg? Exception #"names must start with" (start "/bad/name"))))

(deftest request-with-a-topic-should-throw
  (is (thrown? AssertionError (request "/topic/foo" "biscuit"))))

(deftest respond-with-a-topic-should-throw
  (is (thrown? AssertionError (respond "/topic/foo" str))))
