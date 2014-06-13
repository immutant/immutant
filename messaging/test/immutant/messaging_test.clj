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

(deftest queue-topic-should-work
  (is (queue "foo"))
  (is (topic "bar")))

(deftest queue-should-accept-kwargs
  (is (queue "babar" :durable? true)))

(deftest queue-should-validate-opts
  (is (thrown? IllegalArgumentException (queue "baz" :bad :option))))

(deftest topic-should-validate-opts
  (is (thrown? IllegalArgumentException (topic "baz" :bad :option))))

(deftest destination-should-be-stoppable
  (stop (queue "foo")))

(deftest session-should-work
  (with-open [s (session)]
    (is s)))

(deftest session-with-mode-should-work
  (with-open [s (session :mode :transacted)]
    (is s)))

(deftest session-should-throw-with-invalid-mode
  (is (thrown? IllegalArgumentException
        (session :mode :blarg))))

(deftest connection-should-work
  (with-open [c (connection)]
    (is c)
    (is (instance? Connection c))))

(deftest connection-should-accept-kwargs-and-map
  (with-open [c (connection :host "localhost")]
    (is c))
  (with-open [c (connection {:host "localhost"})]
    (is c)))

(deftest connection-should-validate-opts
  (is (thrown? IllegalArgumentException (connection :bad :option))))

(deftest publish-receive-should-work
  (let [q (queue "foo" :durable false)]
    (publish q "hi")
    (is (= "hi" (receive q)))))

(deftest publish-should-accept-kwargs-and-map
  (publish (queue "foo") "hi" :encoding :none)
  (publish (queue "foo") "hi" {:encoding :none}))

(deftest publish-should-validate-opts
  (is (thrown? IllegalArgumentException (publish (queue "foo") 1 :bad :option))))

(deftest listen-should-work
  (let [p (promise)]
    (let [q (queue "listen-queue")]
      (with-open [listener (listen q #(deliver p %))]
        (publish q :hi)
        (is (= :hi (deref p 2000 :fail)))))))

(deftest durable-subscriber
  (let [called (atom (promise))
        t (topic "subscribe")
        listener (subscribe t "my-sub" #(deliver @called %))]
    (publish t :hi)
    (is (= :hi (deref @called 1000 :fail)))
    (reset! called (promise))
    (stop listener)
    (publish t :hi-again)
    (is (= :fail (deref @called 100 :fail)))
    (with-open [listener (subscribe t "my-sub" #(deliver @called %))]
      (is (= :hi-again (deref @called 1000 :fail))))
    (unsubscribe t "my-sub")
    (reset! called (promise))
    (stop listener)
    (publish t :failure)
    (with-open [listener (subscribe t "my-sub" #(deliver @called %))]
      (is (= :success (deref @called 1000 :success))))))

(def ^:dynamic *dvar* :unbound)

(deftest listen-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (let [p (promise)
          q (queue "listen-queue")]
      (with-open [listener (listen q (fn [_] (deliver p *dvar*)))]
        (publish q :whatevs)
        (is (= :bound (deref p 2000 :fail)))))))

(deftest request-respond-should-work
  (let [q (queue "req-resp")]
    (with-open [listener (respond q keyword)]
      (is (= :hi (deref (request q "hi") 10000 :fail))))))

(deftest respond-future-should-handle-a-timeout
  (let [q (queue "req-resp")]
    (with-open [listener (respond q (fn [m] (Thread/sleep 100) (keyword m)))]
      (let [response (request q "hi")]
        (is (= :timeout (deref response 1 :timeout)))
        (is (= :hi (deref response 1000 :timeout)))))))

(deftest respond-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (let [q (queue "rr-queue" :durable false)]
      (with-open [listener (respond q (fn [_] *dvar*))]
        (is (= :bound (deref (request q :whatevs) 2000 :fail)))))))

(deftest request-respond-should-coordinate-results
  (let [q (queue "rr-queue" :durable false)]
    (with-open [listener (respond q (fn [m] (Thread/sleep m) m) :concurrency 5)]
      (let [r1 (request q 50)
            r2 (request q 100)
            r3 (request q 25)]
        (is (= 50 (deref  r1 2000 :fail)))
        (is (= 100 (deref r2 2000 :fail)))
        (is (= 25 (deref r3 2000 :fail)))))))
