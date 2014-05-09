;; Copyright 2014 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.messaging-test
  (:require [clojure.test :refer :all]
            [immutant.messaging :refer :all])
  (:import org.projectodd.wunderboss.messaging.Connection))

(use-fixtures :once
  (fn [f]
    (try
      (f)
      (finally
        (immutant.util/reset)
        ))))

(deftest endpoint-should-work
  (let [ep (endpoint "foo")]
    (is ep)
    (is (not (.isBroadcast ep))))
  (let [ep (endpoint "bar" {:broadcast true})]
    (is ep)
    (is (.isBroadcast ep))))

(deftest endpoint-should-accept-kwargs
  (let [ep (endpoint "bar" :broadcast true)]
    (is ep)
    (is (.isBroadcast ep))))

(deftest endpoint-should-validate-opts
  (is (thrown? IllegalArgumentException (endpoint "baz" :bad :option))))

(deftest connection-should-work
  (with-open [c (connection)]
    (is c)
    (is (instance? Connection c))))

(deftest connection-should-accept-kwargs-and-map
  (with-open [c (connection :subscription nil)]
    (is c))
  (with-open [c (connection {:subscription nil})]
    (is c)))

(deftest connection-should-validate-opts
  (is (thrown? IllegalArgumentException (connection :bad :option))))

(deftest publish-receive-should-work
  (let [ep (endpoint "foo" :durable false)]
    (publish ep "hi")
    (is (= "hi" (receive ep)))))

(deftest publish-should-accept-kwargs-and-map
  (publish (endpoint "foo") "hi" :encoding :text)
  (publish (endpoint "foo") "hi" {:encoding :text}))

(deftest publish-should-validate-opts
  (is (thrown? IllegalArgumentException (publish (endpoint "foo") 1 :bad :option))))

(deftest receive-with-a-durable-subscription

  (with-open [topic (endpoint "subs" :broadcast? true :durable? false)]
    (publish topic :hi)
    (is (nil? (receive topic :timeout -1)))
    (with-open [sub (subscribe topic "foobar")]
      (publish topic :hi2)
      (is (= :hi2 (receive topic :timeout 100 :subscription sub)))))

  (with-open [topic (endpoint "subs2" :broadcast? true :durable? false)
              sub (subscribe topic "baz")
              connection (connection :subscription sub)]
    (publish topic :hi2 :connection connection)
    (is (= :hi2 (receive topic
                  {:timeout 100 :connection connection :subscription sub})))))

(deftest listen-should-work
  (let [p (promise)]
    (with-open [ep (endpoint "listen-queue")
                listener (listen ep #(deliver p %))]
      (publish ep :hi)
      (is (= :hi (deref p 2000 :fail))))))

(deftest listen-with-durable-subscriber
  (let [called (atom (promise))]
    (with-open [ep (endpoint "listen-topic" :broadcast? true :durable? false)
                sub (subscribe ep "a-name")]
      (let [listener (listen ep #(deliver @called %)
                       :subscription sub)]
        (publish ep :hi)
        (is (= :hi (deref @called 1000 :fail)))
        (reset! called (promise))
        (stop listener)
        (publish ep :hi-again)
        (is (= :fail (deref @called 100 :fail)))
        (with-open [listener (listen ep #(deliver @called %)
                               :subscription sub)]
          (is (= :hi-again (deref @called 1000 :fail))))))))

(def ^:dynamic *dvar* :unbound)

(deftest listen-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (let [p (promise)]
      (with-open [ep (endpoint "listen-queue")
                  listener (listen ep (fn [_] (deliver p *dvar*)))]
        (publish ep :whatevs)
        (is (= :bound (deref p 2000 :fail)))))))

(deftest request-respond-should-work
  (with-open [ep (endpoint "req-resp")
              listener (respond ep keyword)]
    (is (= :hi (deref (request ep "hi") 10000 :fail)))))

(deftest respond-future-should-handle-a-timeout
  (with-open [ep (endpoint "req-resp")
              listener (respond ep (fn [m] (Thread/sleep 100) (keyword m)))]
    (let [response (request ep "hi")]
      (is (= :timeout (deref response 1 :timeout)))
      (is (= :hi (deref response 1000 :timeout))))))

(deftest respond-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (with-open [ep (endpoint "rr-queue" :durable false)
                listener (respond ep (fn [_] *dvar*))]
      (is (= :bound (deref (request ep :whatevs) 2000 :fail))))))

(deftest request-respond-should-coordinate-results
  (with-open [ep (endpoint "rr-queue" :durable false)
              listener (respond ep (fn [m] (Thread/sleep m) m) :concurrency 5)]
    (let [r1 (request ep 50)
          r2 (request ep 100)
          r3 (request ep 25)]
      (is (= 50 (deref  r1 2000 :fail)))
      (is (= 100 (deref r2 2000 :fail)))
      (is (= 25 (deref r3 2000 :fail))))))

;; TODO: test stop
