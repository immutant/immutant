;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns test.immutant.memoize
  (:use immutant.cache
        clojure.test)
  (:require [clojure.core.memoize :as cm]))

(defmacro timeit [& body]
  `(let [t# (System/nanoTime)] ~@body (/ (- (System/nanoTime) t#) 1000000000.0)))

(deftest should-memoize
  (let [f (fn [] (Thread/sleep 1000) "boo")
        m (memo f "test")]
    (is (> (timeit (m)) 1))
    (is (< (timeit (m)) 1))
    (is (= (m) "boo"))))

(deftest prepopulation
  (let [plus (memo + "wrong" :seed {[3 5] 9})]
    (is (= 9 (plus 3 5)))))

(deftest only-first-pays-total-cost
  (let [f (fn [x] (Thread/sleep 1000) x)
        m (memo f "first")
        f1 (future (timeit (m 42)))]
    (Thread/sleep 500)
    (is (< (timeit (m 42)) 1))
    (is (> @f1 1))))
  
