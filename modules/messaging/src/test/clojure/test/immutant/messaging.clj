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
  (:require [immutant.registry :as registry]))

(def created-dests (atom #{}))

(def mock-registry
  {"destinationizer"
   (proxy [org.immutant.messaging.Destinationizer] [nil]
     (createQueue [name & _]
       (swap! created-dests conj name)
        false)
     (createTopic [name & _]
       (swap! created-dests conj name)
       false))})

(defn test-already-running [destination]
  (let [names (atom (keys mock-registry))]
    (with-redefs [registry/get (fn [k]
                                 (swap! names (partial remove #(= % k)))
                                 (mock-registry k))]
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
  (is (thrown-with-msg? Exception #"names must contain" (start "/bad/name"))))

(deftest request-with-a-topic-should-throw
  (is (thrown? AssertionError (request "/topic/foo" "biscuit"))))

(deftest respond-with-a-topic-should-throw
  (is (thrown? AssertionError (respond "/topic/foo" str))))

(defn test-as-fn [dest]
  (reset! created-dests #{})
  (with-redefs [registry/get #(mock-registry %)]
    (start dest))
  (is (some #{(str dest)} @created-dests)))

(deftest bad-destination-name-as-queue
  (test-as-fn (as-queue "/bad/name")))

(deftest bad-destination-name-as-topic
  (test-as-fn (as-topic "/bad/name")))
