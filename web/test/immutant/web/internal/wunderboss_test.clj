;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.internal.wunderboss-test
  (:require [clojure.test :refer :all]
            [immutant.web.internal.wunderboss :refer :all])
  (:import [java.util LinkedHashMap]))

(deftest filter-mappings
  (are [x expected] (= (-> x add-ws-filter keys) expected)
       nil                        ["ws-helper"]
       {}                         ["ws-helper"]
       {:a 1}                     [:a "ws-helper"]
       (array-map)                ["ws-helper"]
       (array-map :a 1 :b 2)      [:a :b "ws-helper"]
       (LinkedHashMap.)           ["ws-helper"]
       (LinkedHashMap. {:a 1})    [:a "ws-helper"]
       (doto (LinkedHashMap.)
         (.put :a 1)
         (.put :b 2))             [:a :b "ws-helper"]))
