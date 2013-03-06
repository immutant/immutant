;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns immutant.integs.msg.topics
  (:use fntest.core
        clojure.test
        immutant.messaging))

(def gravy "/topic/gravy")
(def oddball (as-topic "toddball"))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/topics"
                       }))

(deftest publish-to-multiple-subscribers
  (let [msgs (pmap (fn [_] (receive gravy)) (range 10))]
    (Thread/sleep 1000)                 ; give subscribers some time 
    (publish gravy "biscuit")
    (is (= 10 (count msgs)))
    (is (every? (partial = "biscuit") msgs))))

(deftest durable-topic-subscriber
  "First call to receive with a client-id establishes the durable subscriber"
  (receive gravy :client-id "bacon" :timeout 1)
  (publish gravy "ham#1")
  (publish gravy "ham#2")
  (is (= "ham#1" (receive gravy :client-id "bacon")))
  (is (= "ham#2" (receive gravy :client-id "bacon")))
  (unsubscribe "bacon")
  (publish gravy "ham#3")
  (is (nil? (receive gravy :client-id "bacon" :timeout 100)))
  (publish gravy "ham#4")
  (is (= "ham#4" (receive gravy :client-id "bacon" :timeout 100)))
  (unsubscribe "bacon"))

(deftest publish-and-receive-using-as-topic
  (let [result (future (receive oddball))]
    (Thread/sleep 1000)
    (publish oddball "ahoy")
    (is (= "ahoy" @result))))

(deftest topic-listeners-should-append
  (publish "topic.198" 42)
  (is (= #{41 43} (set (take 2 (message-seq "queue.198"))))))
