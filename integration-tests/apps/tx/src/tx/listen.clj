(ns tx.listen
  (:use clojure.test)
  (:require [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [tx.core :as core]))

(imsg/start "/queue/trigger")

(defn listener [m]
  (imsg/publish "/queue/test" "kiwi")
  (imsg/publish "/queue/remote-test" "starfruit" :host "localhost" :port 5445)
  (ic/put core/cache :a 1)
  (if (:throw? m) (throw (Exception. "rollback"))))

(imsg/listen "/queue/trigger" listener)

(defn trigger-listener
  [throw?]
  (imsg/publish "/queue/trigger" {:throw? throw?}))

(use-fixtures :each core/cache-fixture)

(deftest transactional-writes-in-listener-should-work
  (trigger-listener false)
  (is (= "kiwi" (imsg/receive "/queue/test" :timeout 2000)))
  (is (= "starfruit" (imsg/receive "/queue/remote-test")))
  (is (= 1 (:a core/cache))))

(deftest transactional-writes-in-listener-should-fail-on-rollback
  (trigger-listener true)
  (is (nil? (imsg/receive "/queue/test" :timeout 2000)))
  (is (nil? (imsg/receive "/queue/remote-test" :timeout 2000)))
  (is (nil? (:a core/cache))))
