;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

(ns test.immutant.cache
  (:refer-clojure :exclude (swap!))
  (:use immutant.cache
        clojure.test)
  (:require [clojure.core.cache :as core]
            [clojure.java.io :as io])
  (:import java.util.concurrent.TimeUnit))

(deftest test-lookup-by-list
  (is (= :foo (core/lookup (core/miss (create "foo") '(bar) :foo) '(bar)))))

(deftest test-local-infinispan-cache
  (testing "counts"
    (is (= 0 (count (create "0"))))
    (is (= 1 (count (create "1" :seed {:a 1})))))
  (testing "lookup using keywords"
    (let [c (create "keywords" :seed {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (:a c)
           2   (:b c)
           42  (:X c 42)
           nil (:X c))))
  (testing "lookups using .lookup"
    (let [c (create "lookups" :seed {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (.lookup c :a)
           2   (.lookup c :b)
           ;; 42  (.lookup c :c 42)
           nil (.lookup c :c))))
  (testing "gets and cascading gets"
    (let [c (create "gets" :seed {:a 1, :b 2, :c {:d 3, :e 4}, :f nil, :g false, nil {:h 5}})]
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
    (let [c (create "finding" :seed {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (find c :a) [:a 1]
           (find c :b) [:b 2]
           (find c :c) nil
           (find c nil) nil)))
  (testing "that contains? works for cache"
    (let [c (create "contains" :seed {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (contains? c :a) true
           (contains? c :b) true
           (contains? c :c) false
           (contains? c nil) false))))

(deftest test-put
  (let [c (create "puts")
        v {:foo [1 2 3] "p" "q"}]
    (is (nil? (put c :a v)))
    (is (= v (get c :a)))
    (is (= v (put c :a "next")))))

(deftest test-put-nil
  (let [c (create "nilly")]
    (is (= :right (:a c :right)))
    (is (nil? (put c :a nil)))
    (is (nil? (:a c :wrong)))
    (is (nil? (put c nil :right)))
    (is (= :right (get c nil :wrong)))))

(deftest test-put-ttl
  (let [c (create "ttl" :seed {})]
    (put c :a 1 {:ttl [500 :milliseconds]})
    (is (= 1 (get c :a)))
    (Thread/sleep 550)
    (is (nil? (get c :a)))))

(deftest test-put-default-ttl
  (let [c (create "ttl" :seed {} :ttl 500 :units :days)]
    (put c :a 1 {:units :milliseconds})
    (is (= 1 (get c :a)))
    (Thread/sleep 550)
    (is (nil? (get c :a)))))

(deftest test-put-idle
  (let [c (create "idle")]
    (put c :a 1 {:idle [500 :milliseconds]})
    (Thread/sleep 300)
    (is (= 1 (get c :a)))
    (Thread/sleep 300)
    (is (= 1 (get c :a)))
    (Thread/sleep 550)
    (is (nil? (get c :a)))))

(deftest test-put-if-absent-ttl
  (let [c (create "absent")]
    (is (nil? (:a c)))
    (is (nil? (put-if-absent c :a 1 {:ttl [300 :milliseconds]})))
    (is (= 1 (:a c)))
    (is (= 1 (put-if-absent c :a 2)))
    (is (= 1 (:a c)))
    (Thread/sleep 350)
    (is (nil? (:a c)))))

(deftest test-put-all-ttl
  (let [c (create "all")]
    (is (= 0 (count c)))
    (put-all c {:a 1 :b 2} {:ttl [300 :milliseconds]})
    (is (= 1 (:a c)))
    (is (= 2 (:b c)))
    (Thread/sleep 400)
    (is (nil? (:a c)))
    (is (nil? (:b c)))))

(deftest test-put-if-present
  (let [c (create "present" :seed {:a 1})]
    (is (nil? (put-if-present c :b 2)))
    (is (nil? (:b c)))
    (is (= 1 (put-if-present c :a 2)))
    (is (= 2 (:a c)))))

(deftest test-put-if-replace
  (let [c (create "replace" :seed {:a 1})]
    (is (false? (put-if-replace c :a 2 3)))
    (is (= 1 (:a c)))
    (is (true? (put-if-replace c :a 1 2)))
    (is (= 2 (:a c)))))

(deftest test-delete
  (let [c (create "delete" :seed {:a 1 :b 2})]
    (is (false? (delete c :a 2)))
    (is (= 2 (count c)))
    (is (true? (delete c :a 1)))
    (is (= 1 (count c)))
    (is (nil? (delete c :missing)))
    (is (= 2 (delete c :b)))
    (is (empty? c))))

(deftest test-delete-all
  (let [c (create "clear" :seed {:a 1 :b 2})]
    (is (= 2 (count c)))
    (is (= 0 (count (delete-all c))))
    (is (= 0 (count c)))))

(deftest test-swapping
  (let [c (create "swap" :seed {:a 1, :b nil})]
    (is (= 2 (swap! c :a inc)))
    (is (= 1 (swap! c :b (fnil inc 0))))
    (is (thrown? IllegalArgumentException (swap! c :c (fnil inc 0))))))

(deftest test-seeding
  (let [c (create "foo" :seed {:a 1})
        d (lookup "foo")]
    (is (= (:a c) (:a d) 1))
    (put c :b 2)
    (is (= (:b c) (:b d) 2))
    (let [e (create "foo" :seed {})]
      (is (empty? e)))))

(deftest test-persistent-seeding
  (let [c (create "cachey" :persist "src/test/resources/cache-store")
        cn (create "cachey-none" :encoding :none, :persist "src/test/resources/cache-store")]
    (is (= (:key c) 42))
    (is (= (:key cn) 42))))

(deftest test-persist-file-store
  (try
    (create "mike" :persist true)
    (is (or (.exists (io/file "Infinispan-FileCacheStore/mike"))
            (.exists (io/file "Infinispan-SingleFileStore/mike.dat"))))
    (finally
      (io/delete-file "Infinispan-SingleFileStore/mike.dat" :silently)
      (io/delete-file "Infinispan-SingleFileStore" :silently)
      (io/delete-file "Infinispan-FileCacheStore/mike" :silently)
      (io/delete-file "Infinispan-FileCacheStore" :silently))))

(deftest test-persist-file-store-with-parents
  (let [dir (io/file "target/gin/tonic")]
    (try
      (create "chas" :persist (str dir))
      (is (.exists dir))
      (finally
        (io/delete-file (io/file dir "chas") :silently)
        (io/delete-file (io/file dir "chas.dat") :silently)
        (io/delete-file dir)
        (io/delete-file (.getParent dir))))))

(deftest test-create-restarts
  (let [c (create "terrence")]
    (put c :a 1)
    (is (= 1 (:a c)))
    (empty? (create "terrence"))
    (is (thrown? IllegalStateException (empty? c)))))

(deftest test-eviction
  (let [c (create "nelly" :max-entries 2)]
    (put c :a 1)
    (put c :b 2)
    (put c :c 3)
    (is (nil? (:a c)))
    (is (= 2 (count c)))))

(deftest test-seqable
  (let [seed {:a 1, :b {:c 42}}
        c (create "seedy" :seed seed)]
    (is (= seed (into {} (seq c))))))

(deftest default-to-local "should not raise exception"
  (create "remote" :mode :replicated))

(deftest lookup-of-stopped-cache "should return nil"
  (let [c (create "stopped")]
    (.stop (.cache c))
    (is (nil? (lookup "stopped")))))

(defmacro test-invalid-opts [form]
  `(is (~'thrown-with-msg? IllegalArgumentException
         #"is not a valid option" ~form)))

(deftest invalid-opts
  (test-invalid-opts (create "foo" :ham :biscuit))
  (test-invalid-opts (lookup "foo" :ham :biscuit))
  (test-invalid-opts (lookup-or-create "foo" :ham :biscuit))
  (test-invalid-opts (memo println "foo" :ham :biscuit)))
