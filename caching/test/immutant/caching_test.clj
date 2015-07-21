;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.caching-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [immutant.caching :refer :all]
            [immutant.util :refer [in-container? set-log-level!]]
            [immutant.codecs :refer [encode decode]]
            [immutant.codecs.fressian :refer [register-fressian-codec]]
            [clojure.core.cache :refer [lookup miss seed]]
            immutant.caching.core-cache)
  (:import org.infinispan.configuration.cache.CacheMode
           org.infinispan.notifications.cachelistener.event.Event$Type))

(set-log-level! (or (System/getenv "LOG_LEVEL") :OFF))

(use-fixtures :once
  (fn [f]
    (register-fressian-codec)
    (f)))

(defn new-cache [& options]
  (stop "test")
  (let [result (apply cache "test" options)]
    (doseq [i (.getListeners result)] (.removeListener result i))
    result))

(deftest test-lookup-by-list
  (is (= :foo (lookup (miss (new-cache) '(bar) :foo) '(bar)))))

(deftest test-local-infinispan-cache
  (testing "counts"
    (is (= 0 (count (new-cache))))
    (is (= 1 (count (seed (new-cache) {:a 1})))))
  (testing "lookup using keywords"
    (let [c (seed (new-cache) {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (:a c)
           2   (:b c)
           42  (:X c 42)
           nil (:X c))))
  (testing "lookups using .lookup"
    (let [c (seed (new-cache) {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (lookup c :a)
           2   (lookup c :b)
           42  (lookup c :c 42)
           nil (lookup c :c))))
  (testing "gets and cascading gets"
    (let [c (seed (-> (new-cache) (with-codec :edn)) {:a 1, :b 2, :c {:d 3, :e 4}, :f nil, :g false, nil {:h 5}})]
      (are [actual expect] (= expect actual)
           (get c :a) 1
           (get c :e) nil
           (get c :e 0) 0
           (get c :b 0) 2
           (get c :f 0) nil
           (get-in c [:c :e]) 4
           (get-in c '(:c :e)) 4
           (get-in c [:c :x]) nil
           (get-in c [:f]) nil
           (get-in c [:g]) false
           (get-in c [:h]) nil
           (get-in c []) c
           (get-in c nil) c
           (get-in c [:c :e] 0) 4
           (get-in c '(:c :e) 0) 4
           (get-in c [:c :x] 0) 0
           (get-in c [:b] 0) 2
           (get-in c [:f] 0) nil
           (get-in c [:g] 0) false
           (get-in c [:h] 0) 0
           (get-in c [:x :y] {:y 1}) {:y 1}
           (get-in c [] 0) c
           (get-in c nil 0) c)))
  (testing "that finding works for cache"
    (let [c (seed (-> (new-cache) (with-codec :edn)) {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (find c :a) [:a 1]
           (find c :b) [:b 2]
           (find c :c) nil
           (find c nil) nil)))
  (testing "that contains? works for cache"
    (let [c (seed (-> (new-cache) (with-codec :edn)) {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (contains? c :a) true
           (contains? c :b) true
           (contains? c :c) false
           (contains? c nil) false))))

(deftest test-put
  (let [c (new-cache)
        v {:foo [1 2 3] "p" "q"}]
    (is (nil? (.put c :a v)))
    (is (= v (get c :a)))
    (is (= v (.put c :a "next")))))

(deftest test-put-nil
  (let [c (-> (new-cache) (with-codec :edn))]
    (is (= :right (:a c :right)))
    (is (nil? (.put c :a nil)))
    (is (nil? (:a c :wrong)))
    (is (nil? (.put c nil :right)))
    (is (= :right (get c nil :wrong)))))

(deftest test-put-ttl
  (let [c (new-cache :ttl 200)]
    (.put c :a 1)
    (is (= 1 (get c :a)))
    (Thread/sleep 250)
    (is (nil? (get c :a)))))

(deftest test-put-idle
  (let [c (new-cache :idle 200)]
    (.put c :a 1)
    (dotimes [_ 4]
      (Thread/sleep 50)
      (is (= 1 (get c :a))))
    (Thread/sleep 250)
    (is (nil? (get c :a)))))

(deftest test-put-if-absent-ttl
  (let [c (new-cache :ttl 500)]
    (is (nil? (:a c)))
    (is (nil? (.putIfAbsent c :a 1)))
    (is (= 1 (:a c)))
    (.put c :a 1)                       ; reset timer
    (is (= 1 (.putIfAbsent c :a 2)))
    (is (= 1 (:a c)))
    (Thread/sleep 600)
    (is (nil? (:a c)))))

(deftest test-put-all-ttl
  (let [c (new-cache :ttl 500)]
    (is (= 0 (count c)))
    (.putAll c {:a 1 :b 2})
    (is (= 1 (:a c)))
    (is (= 2 (:b c)))
    (Thread/sleep 600)
    (is (nil? (:a c)))
    (is (nil? (:b c)))))

(deftest test-replace-key
  (let [c (seed (new-cache) {:a 1})]
    (is (nil? (.replace c :b 2)))
    (is (nil? (:b c)))
    (is (= 1 (.replace c :a 2)))
    (is (= 2 (:a c)))))

(deftest test-replace-value
  (let [c (seed (new-cache) {:a 1})]
    (is (false? (.replace c :a 2 3)))
    (is (= 1 (:a c)))
    (is (true? (.replace c :a 1 2)))
    (is (= 2 (:a c)))))

(deftest test-remove
  (let [c (seed (new-cache) {:a 1 :b 2})]
    (is (false? (.remove c :a 2)))
    (is (= 2 (count c)))
    (is (true? (.remove c :a 1)))
    (is (= 1 (count c)))
    (is (nil? (.remove c :missing)))
    (is (= 2 (.remove c :b)))
    (is (empty? c))))

(deftest test-delete-all
  (let [c (seed (new-cache) {:a 1 :b 2})]
    (is (= 2 (count c)))
    (is (= 0 (count (.clear c))))
    (is (= 0 (count c)))))

(deftest test-cas
  (let [c (seed (new-cache) {:a 1, :b nil})]
    (is (= 2 (swap-in! c :a inc)))
    (is (= 1 (swap-in! c :b (fnil inc 0))))
    (is (= 1 (swap-in! c :c (fnil inc 0))))
    (is (= (into {} (seq c)) {:a 2, :b 1, :c 1}))))

(deftest test-persist-file-store
  (try
    (let [mike (cache "mike" :persist true)]
      (is (or (.exists (io/file "Infinispan-FileCacheStore/mike"))
            (.exists (io/file "Infinispan-SingleFileStore/mike.dat"))))
      (.stop mike))
    (finally
      (io/delete-file "Infinispan-SingleFileStore/mike.dat" :silently)
      (io/delete-file "Infinispan-SingleFileStore" :silently)
      (io/delete-file "Infinispan-FileCacheStore/mike" :silently)
      (io/delete-file "Infinispan-FileCacheStore" :silently))))

(deftest test-persist-file-store-with-parents
  (let [dir (io/file "target/gin/tonic")]
    (try
      (let [chas (cache "chas" :persist (str dir))]
        (is (.exists dir))
        (.stop chas))
      (finally
        (io/delete-file (io/file dir "chas") :silently)
        (io/delete-file (io/file dir "chas.dat") :silently)
        (io/delete-file dir)
        (io/delete-file (.getParent dir))))))

(deftest test-create-restarts
  (let [c (cache "terrence")]
    (.put c :a 1)
    (is (= 1 (:a c)))
    (stop "terrence")
    (is (empty? (cache "terrence")))
    (is (thrown? IllegalStateException (empty? c)))))

(deftest test-create-reconfigures
  (is (= -1 (.. (new-cache)
              getCacheConfiguration eviction maxEntries)))
  (is (= 42 (.. (new-cache :max-entries 42)
              getCacheConfiguration eviction maxEntries))))

(deftest test-eviction
  (let [c (new-cache :max-entries 2 :eviction :lru)]
    (.put c :a 1)
    (.put c :b 2)
    (.put c :c 3)
    (is (nil? (:a c)))
    (is (= 2 (count c)))))

(deftest test-iteration
  (doseq [codec (-> (immutant.codecs/codec-set) (conj nil) (disj :json))]
    (let [c (if codec
              (with-codec (new-cache) codec)
              (new-cache))]
      (.putAll c {:a 1, :b 2, :c 3})
      (testing (str "codec -> " codec)
        (is (= #{:a :b :c} (.keySet c)))
        (is (= [1 2 3] (sort (.values c))))
        (is (= {:a 1, :b 2, :c 3} (into {} (.entrySet c))))))))

(deftest test-seqable
  (let [seed {:a 1, :b {:c 42}}
        c (seed (new-cache) seed)]
    (is (= seed (into {} (seq c))))))

(deftest various-codecs
  (let [none (new-cache)
        edn (with-codec none :edn)
        json (with-codec none :json)
        fressian (with-codec none :fressian)]
    (.put none :a 1)
    (.put edn :b 2)
    (.put json :c 3)
    (.put fressian :d 4)
    (is (= 1 (:a none)))
    (is (= 2 (:b edn)))
    (is (= 3 (:c json)))
    (is (= 4 (:d fressian)))
    (is (= 2 (decode (get none (encode :b :edn)) :edn)))
    (is (= 3 (decode (get none (encode :c :json)) :json)))
    (is (= 4 (decode (get none (encode :d :fressian)) :fressian)))))

(deftest builder-configs
  (is (= CacheMode/LOCAL (.. (builder)
                           build
                           clustering
                           cacheMode)))
  (is (= CacheMode/REPL_SYNC (.. (builder :mode :repl-sync)
                               build
                               clustering
                               cacheMode))))

(deftest test-with-expiration
  (let [c (new-cache :ttl [42 :days] :idle [2 :weeks])]
    (let [c (with-expiration c :ttl 100)]
      (.put c :a 1)
      (is (= 1 (get c :a)))
      (Thread/sleep 150)
      (is (nil? (get c :a))))
    (let [c (with-expiration c {:idle 100})]
      (.put c :a 1)
      (Thread/sleep 50)
      (is (= 1 (get c :a)))
      (Thread/sleep 50)
      (is (= 1 (get c :a)))
      (Thread/sleep 150)
      (is (nil? (get c :a))))))

(deftest cache-listeners-add-and-remove
  (let [c (new-cache)
        l1 (add-listener! c prn :cache-entry-loaded)
        l2 (add-listener! c prn :cache-entry-visited :cache-entry-removed)]
    (is (= 1 (count l1)))
    (is (= 2 (count l2)))
    (is (= 3 (count (.getListeners c))))
    (.removeListener c (first l1))
    (is (= 2 (count (.getListeners c))))
    (doseq [i l2] (.removeListener c i))
    (is (= 0 (count (.getListeners c))))))

(deftest cache-listeners-should-reject-bad-keyword
  (let [c (new-cache)]
    (is (zero? (count (.getListeners c))))
    (is (thrown? IllegalArgumentException (add-listener! c prn :cache-entry-visited :this-should-barf)))
    (is (zero? (count (.getListeners c))))))

(deftest cache-listeners-should-fire-correct-events
  (let [c (new-cache)
        results (atom [])]
    (add-listener! c (fn [e] (swap! results conj
                              {:type (.getType e),
                               :pre? (.isPre e),
                               :key (.getKey e),
                               :value (.getValue e)}))
      :cache-entry-visited
      :cache-entry-modified
      :cache-entry-created)
    (.put c :a 1)
    (is (= (first @results) {:type Event$Type/CACHE_ENTRY_CREATED, :key :a
                             :pre? true,  :value nil}))
    (is (= (last  @results) {:type Event$Type/CACHE_ENTRY_CREATED, :key :a
                             :pre? false, :value 1}))
    (reset! results [])
    (swap-in! c :a inc)
    (is (= [Event$Type/CACHE_ENTRY_VISITED Event$Type/CACHE_ENTRY_VISITED Event$Type/CACHE_ENTRY_MODIFIED Event$Type/CACHE_ENTRY_MODIFIED]
          (map :type @results)))
    (is (= [true false true false] (map :pre? @results)))
    (is (= [1 1 1 2] (map :value @results)))
    (reset! results [])
    (is (= 2 (:a c)))
    (is (= [Event$Type/CACHE_ENTRY_VISITED Event$Type/CACHE_ENTRY_VISITED]
          (map :type @results)))
    (is (= [true false] (map :pre? @results)))
    (is (= [2 2] (map :value @results)))))

(when (not (in-container?))
  (deftest default-to-local "should not raise exception"
    (is (= "LOCAL" (-> (new-cache :mode :repl-sync)
                     .getCacheConfiguration .clustering .cacheMode
                     str))))

  (deftest test-persistent-seeding
    (let [c (with-codec (cache "cachey" :persist "dev-resources/cache-store") :edn)
          cn (cache "cachey-none" :persist "dev-resources/cache-store")]
      (is (= (:key c) 42))
      (is (= (:key cn) 42)))))
