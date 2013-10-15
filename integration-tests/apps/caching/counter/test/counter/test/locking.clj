(ns counter.test.locking
  (:use clojure.test)
  (:require [immutant.cache :as csh]
            [immutant.messaging :as msg]
            [clojure.tools.logging :as log]))

(msg/start "/queue/work")
(msg/start "/queue/done")

(def caches {:nontx (csh/create "nontx" :tx false)
             :optimistic (csh/create "optimistic" :locking :optimistic)
             :pessimistic (csh/create "pessimistic" :locking :pessimistic)})

(defn work
  [{:keys [name key]}]
  (msg/publish "/queue/done" (csh/swap! (get caches name) key inc)))

(use-fixtures :each (fn [f]
                      (let [listener (msg/listen "/queue/work" work :concurrency 10)]
                        (f)
                        (msg/unlisten listener))))

(deftest correct-counting-with-non-transactional-cache
  (csh/put (:nontx caches) :count 0)
  (is (= 0 (:count (:nontx caches))))
  (dotimes [_ 10] (msg/publish "/queue/work" {:name :nontx :key :count}))
  (is (= (range 1 11) (sort (take 10 (msg/message-seq "/queue/done" :timeout 60000)))))
  (is (= 10 (:count (:nontx caches)))))

(deftest correct-counting-with-pessimistic-locking
  (csh/put (:pessimistic caches) :count 0)
  (is (= 0 (:count (:pessimistic caches))))
  (dotimes [_ 10] (msg/publish "/queue/work" {:name :pessimistic :key :count}))
  (is (= (range 1 11) (take 10 (msg/message-seq "/queue/done" :timeout 60000))))
  (is (= 10 (:count (:pessimistic caches)))))

(deftest correct-counting-with-optimistic-locking
  (csh/put (:optimistic caches) :count 100)
  (is (= 100 (:count (:optimistic caches))))
  (dotimes [_ 3] (msg/publish "/queue/work" {:name :optimistic :key :count}))
  (is (= [101 102 103] (take 3 (msg/message-seq "/queue/done" :timeout 60000))))
  (is (= 103 (:count (:optimistic caches)))))
