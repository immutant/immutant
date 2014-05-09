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

(ns immutant.scheduling.dates-test
  (:require [clojure.test :refer :all]
            [immutant.scheduling.dates :refer :all]
            [immutant.scheduling.periods :refer (as-period)])
  (:import [java.util Date Calendar]))

(def since-epoch 1368779460000)
(def eight-thirty-one (doto (Calendar/getInstance)
                        (.setTimeZone (java.util.TimeZone/getTimeZone "UTC"))
                        (.setTimeInMillis since-epoch)))

(deftest dates
  (let [now (.getTime eight-thirty-one)]
    (testing "simple types"
      (are [x expected] (= expected (as-date x))
           since-epoch    now
           nil            nil
           now            now))
    (testing "string formats"
      (with-redefs [calendar #(.clone eight-thirty-one)]
        (are [x expected] (= (as-period expected) (- (.getTime (as-date x)) since-epoch))
             "1630"  [7 :hours, 59 :minutes]
             "16:30" [7 :hours, 59 :minutes]
             "0900"  [29 :minutes]
             "09:00" [29 :minutes]
             "0700"  [22 :hours, 29 :minutes]
             "07:00" [22 :hours, 29 :minutes]
             "08:30" [23 :hours, 59 :minutes])))))
