;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
  (:use [immutant.cache]
        [clojure.test]
        [clojure.core.cache]))

(deftest test-lookup-by-list
  (is (= :foo (lookup (miss (cache "foo") '(bar) :foo) '(bar)))))

(deftest test-local-infinispan-cache
  (testing "counts"
    (is (= 0 (count (cache "0"))))
    (is (= 1 (count (cache "1" {:a 1})))))
  (testing "lookup using keywords"
    (let [c (cache "keywords" {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (:a c)
           2   (:b c)
           42  (:X c 42)
           nil (:X c))))
  (testing "lookups using .lookup"
    (let [c (cache "lookups" {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           1   (.lookup c :a)
           2   (.lookup c :b)
           ;; 42  (.lookup c :c 42)
           nil (.lookup c :c))))
  (testing "assoc and dissoc"
    (let [c (cache "assoc")]
      (are [expect actual] (= expect actual)
           1   (:a (assoc c :a 1))
           1   (:a (assoc c :b 2))
           2   (:b (dissoc c :a))
           nil (:a (dissoc c :a))
           nil (:b (-> c (dissoc :a) (dissoc :b)))
           0   (count (-> c (dissoc :a) (dissoc :b))))))
  (testing "gets and cascading gets"
    (let [c (cache "gets" {:a 1, :b 2, :c {:d 3, :e 4}, :f nil, :g false, nil {:h 5}})]
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
    (let [c (cache "finding" {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (find c :a) [:a 1]
           (find c :b) [:b 2]
           (find c :c) nil
           (find c nil) nil)))
  (testing "that contains? works for cache"
    (let [c (cache "contains" {:a 1 :b 2})]
      (are [expect actual] (= expect actual)
           (contains? c :a) true
           (contains? c :b) true
           (contains? c :c) false
           (contains? c nil) false))))

