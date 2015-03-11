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

(ns immutant.caching.core-memoize-test
  (:require [clojure.test :refer :all]
            [clojure.core.cache :refer [seed]]
            [immutant.caching :refer [cache stop]]
            [immutant.caching.core-memoize :refer [memo]]))

(defmacro timeit [& body]
  `(let [t# (System/nanoTime)] ~@body (/ (- (System/nanoTime) t#) 1000000000.0)))

(deftest should-memoize
  (stop "test")
  (let [f (fn [] (Thread/sleep 500) "boo")
        m (memo f "test")]
    (is (> (timeit (m)) 0.48))
    (is (< (timeit (m)) 0.5))
    (is (= (m) "boo"))))

(deftest prepopulation
  (stop "wrong")
  (seed (cache "wrong") {[3 5] 9})
  (let [plus (memo + "wrong")]
    (is (= 9 (plus 3 5)))))

(deftest only-first-pays-total-cost
  (stop "first")
  (let [f (fn [x] (Thread/sleep 500) x)
        m (memo f "first")
        f1 (future (timeit (m 42)))]
    (Thread/sleep 500)
    (is (< (timeit (m 42)) 0.5))
    (is (> @f1 0.48))))
