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

(ns immutant.listener-test
  (:require [clojure.test :refer :all]
            [immutant.util :refer (in-container? messaging-remoting-port)]
            [immutant.transactions :refer :all]
            [immutant.caching :as csh]
            [immutant.messaging :as msg]))

(def queue (msg/queue "/queue/test" :durable false))
(msg/queue "remote" :durable false)
(def conn (if (in-container?)
            (msg/connection :host "localhost" :port (messaging-remoting-port)
              :username "testuser" :password "testuser" :remote-type :hornetq-wildfly
              :xa true)
            (msg/connection :host "localhost" :xa true)))
(def remote-queue (msg/queue "remote" :connection conn))
(def trigger (msg/queue "/queue/trigger" :durable false))
(def cache (csh/cache "tx-test" :transactional true))

(defn work [m]
  (msg/publish queue "kiwi")
  (msg/publish remote-queue "starfruit")
  (.put cache :a 1)
  (not-supported
    (.put cache :deliveries (inc (or (:deliveries cache) 0))))
  (if (:throw? m) (throw (Exception. "rollback")))
  (if (:rollback? m) (.setRollbackOnly (manager))))

(defn listener [m]
  (if (:tx? m)
    (required (work m))
    (work m)))

(use-fixtures :each
  (fn [f]
    (.clear cache)
    (f)))

(use-fixtures :once
  (fn [f]
    (f)
    (.close conn)))

(deftest transactional-writes-in-listener-should-work
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true})
    (is (= "kiwi" (msg/receive queue :timeout 1000)))
    (is (= "starfruit" (msg/receive remote-queue :timeout 1000)))
    (is (= 1 (:a cache)))))

(deftest transactional-writes-in-listener-should-fail-on-exception
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true :throw? true})
    (is (nil? (msg/receive queue :timeout 1000)))
    (is (nil? (msg/receive remote-queue :timeout 1000)))
    (is (nil? (:a cache)))
    (is (= 10 (:deliveries cache)))))

(deftest transactional-writes-in-listener-should-fail-on-rollback
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true :rollback? true})
    (is (nil? (msg/receive queue :timeout 1000)))
    (is (nil? (msg/receive remote-queue :timeout 1000)))
    (is (nil? (:a cache)))
    (is (= 10 (:deliveries cache)))))

(deftest non-transactional-writes-in-listener-with-exception
  (with-open [_ (msg/listen trigger listener :transacted false)]
    (msg/publish trigger {:throw? true})
    (is (= 10 (loop [i 0]
                (Thread/sleep 100)
                (if (or (= 50 i) (= 10 (:deliveries cache)))
                  (:deliveries cache)
                  (recur (inc i))))))
    (is (= 1 (:a cache)))
    (is (= (take 10 (repeat "kiwi"))
          (loop [i 10, v []] (if (zero? i) v (recur (dec i) (conj v (msg/receive queue :timeout 1000)))))))
    (is (= (take 10 (repeat "starfruit"))
          (loop [i 10, v []] (if (zero? i) v (recur (dec i) (conj v (msg/receive remote-queue :timeout 1000)))))))))
