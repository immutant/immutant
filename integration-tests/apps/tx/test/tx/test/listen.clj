(ns tx.test.listen
  (:use clojure.test)
  (:require [immutant.util :as util]
            [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [immutant.xa.transaction :as tx]
            [tx.core :as core]))

(imsg/start "/queue/trigger")
(imsg/start "/queue/trigger-no-tx")

(defn listener [m]
  (imsg/publish "/queue/test" "kiwi")
  (imsg/publish "/queue/remote-test" "starfruit" :host "localhost" :port 5445)
  (ic/put core/cache :a 1)
  (tx/not-supported
   (ic/put core/cache :deliveries (inc (or (:deliveries core/cache) 0))))
  (if (:throw? m) (throw (Exception. "rollback")))
  (if (:rollback? m) (tx/set-rollback-only)))

(imsg/listen "/queue/trigger" listener)

(defn trigger-listener
  [& {:as opts}]
  (imsg/publish "/queue/trigger" opts))

(use-fixtures :each core/cache-fixture)

(deftest transactional-writes-in-listener-should-work
  (trigger-listener)
  (is (= "kiwi" (imsg/receive "/queue/test")))
  (is (= "starfruit" (imsg/receive "/queue/remote-test")))
  (is (= 1 (:a core/cache))))

(deftest transactional-writes-in-listener-should-fail-on-exception
  (trigger-listener :throw? true)
  (is (nil? (imsg/receive "/queue/test" :timeout 2000)))
  (is (nil? (imsg/receive "/queue/remote-test" :timeout 2000)))
  (is (nil? (:a core/cache)))
  (is (= 10 (:deliveries core/cache))))

(deftest transactional-writes-in-listener-should-fail-on-rollback
  (trigger-listener :rollback? true)
  (is (nil? (imsg/receive "/queue/test" :timeout 2000)))
  (is (nil? (imsg/receive "/queue/remote-test" :timeout 2000)))
  (is (nil? (:a core/cache)))
  (is (= 10 (:deliveries core/cache))))

(deftest non-transactional-writes-in-listener-with-exception
  (deref (imsg/listen "/queue/trigger-no-tx" listener :xa false))
  (imsg/publish "/queue/trigger-no-tx" {:throw? true})
  (is (util/wait-for #(= 10 (:deliveries core/cache))))
  (is (= 1 (:a core/cache)))
  (is (every? #(= "kiwi" %) (take 10 (imsg/message-seq "/queue/test"))))
  (is (every? #(= "starfruit" %) (take 10 (imsg/message-seq "/queue/remote-test")))))

