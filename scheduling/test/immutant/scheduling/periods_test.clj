;; Copyright 2014 Red Hat, Inc, and individual contributors.
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

(ns immutant.scheduling.periods-test
  (:require [clojure.test :refer :all]
            [immutant.scheduling.periods :refer :all]))

(deftest periods
  (testing "simple numbers"
    (are [x expected] (= expected (as-period x))
         42   42
         -42  nil
         0    nil))
  (testing "period aliases"
    (are [x expected] (= expected (as-period x))
         :second   1000
         :seconds  1000
         :minute   60000
         :minutes  60000
         :hour     3600000
         :hours    3600000
         :day      86400000
         :days     86400000
         :week     604800000
         :weeks    604800000)
    (is (thrown? IllegalArgumentException (as-period :unknown))))
  (testing "period specs"
    (are [x expected] (= expected (as-period x))
         [4 :seconds]            4000
         [1 :hour]               3600000
         [2 :hours, 4 :seconds]  7204000)
    (is (thrown? NullPointerException (as-period [nil nil])))))
