(ns counter.test.locking
  (:use clojure.test)
  (:require [immutant.cache :as csh]
            [immutant.messaging :as msg]))

(msg/start "/queue/work")
(msg/start "/queue/done")

(def caches {:default (csh/cache "default"),
             :optimistic (csh/cache "optimistic" :locking :optimistic)
             :pessimistic (csh/cache "pessimistic" :locking :pessimistic)})

(defn work
  [{:keys [name key]}]
  (let [cache (get caches name)]
    (loop [v (get cache key)]
      (if (csh/put-if-replace cache key v (inc v))
        (msg/publish "/queue/done" :success)
        (recur (get cache key))))))

(use-fixtures :each (fn [f]
                      (let [listener (msg/listen "/queue/work" work :concurrency 10)]
                        (f)
                        (msg/unlisten listener))))

(deftest correct-counting-with-pessimistic-locking
  (csh/put (:pessimistic caches) :count 0)
  (dotimes [_ 10] (msg/publish "/queue/work" {:name :pessimistic :key :count}))
  (is (every? (partial = :success) (take 10 (msg/message-seq "/queue/done"))))
  (is (= 10 (:count (:pessimistic caches)))))

(deftest correct-counting-with-optimistic-locking
  (csh/put (:optimistic caches) :count 0)
  (dotimes [_ 3] (msg/publish "/queue/work" {:name :optimistic :key :count}))
  (is (every? (partial = :success) (take 3 (msg/message-seq "/queue/done"))))
  (is (= 3 (:count (:optimistic caches)))))
