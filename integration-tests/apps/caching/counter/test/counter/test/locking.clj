(ns counter.test.locking
  (:use clojure.test)
  (:require [immutant.cache :as csh]
            [immutant.messaging :as msg]
            [clojure.tools.logging :as log]))

(msg/start "/queue/work")
(msg/start "/queue/done")

(def caches {:default (csh/cache "default"),
             :optimistic (csh/cache "optimistic" :locking :optimistic)
             :pessimistic (csh/cache "pessimistic" :locking :pessimistic)})

(defn work
  [{:keys [name key]}]
  (let [cache (get caches name)]
    (log/info "JC: work start" name cache)
    (loop [v (get cache key)]
      (log/info "JC: v=" name v)
      (if (csh/put-if-replace cache key v (inc v))
        (msg/publish "/queue/done" name)
        (recur (get cache key))))))

(use-fixtures :each (fn [f]
                      (let [listener (msg/listen "/queue/work" work :concurrency 10)]
                        (log/info "JC: listener for /queue/work set")
                        (f)
                        (msg/unlisten listener))))

(deftest correct-counting-with-pessimistic-locking
  (csh/put (:pessimistic caches) :count 0)
  (is (= 0 (:count (:pessimistic caches))))
  (dotimes [_ 10] (msg/publish "/queue/work" {:name :pessimistic :key :count}))
  (log/info "JC: published 10 messages")
  (is (every? (partial = :pessimistic) (take 10 (msg/message-seq "/queue/done" :timeout 60000))))
  (log/info "JC: pessimistic done" (:count (:pessimistic caches)))
  (is (= 10 (:count (:pessimistic caches)))))

(deftest correct-counting-with-optimistic-locking
  (println "PENDING: fix prior to release")
  ;; (csh/put (:optimistic caches) :count 0)
  ;; (is (= 0 (:count (:optimistic caches))))
  ;; (dotimes [_ 3] (msg/publish "/queue/work" {:name :optimistic :key :count}))
  ;; (log/info "JC: published 3 messages")
  ;; (is (every? (partial = :optimistic) (take 3 (msg/message-seq "/queue/done" :timeout 60000))))
  ;; (log/info "JC: optimistic done" (:count (:optimistic caches)))
  ;; (is (= 3 (:count (:optimistic caches))))
  )
