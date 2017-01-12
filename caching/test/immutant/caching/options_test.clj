;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
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

(ns immutant.caching.options-test
  (:require [clojure.test :refer :all]
            [immutant.caching.options :refer :all]))

(deftest period-specs
  (let [m {:ttl [42 :days, 20 :minutes]
           :idle [19 :weeks]
           :foo 42}
        clean (wash m)]
    (is (= (:ttl  clean) (+ (* 42 24 60 60 1000) (* 20 60 1000))))
    (is (= (:idle clean) (* 19 7 24 60 60 1000)))
    (is (= (:foo  clean) 42))))

(deftest keywords->strings
  (is (= "repl_sync" (-> {:mode :repl-sync} wash :mode)))
  (is (= "pessimistic" (-> {:locking :pessimistic} wash :locking)))
  (is (= "lru" (-> {:eviction :lru} wash :eviction))))
