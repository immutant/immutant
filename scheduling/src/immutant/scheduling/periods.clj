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

(ns ^{:no-doc true} immutant.scheduling.periods)

(def period-aliases
  {:second  1000
   :seconds :second
   :minute  (* 60 1000)
   :minutes :minute
   :hour    (* 60 60 1000)
   :hours   :hour
   :day     (* 24 60 60 1000)
   :days    :day
   :week    (* 7 24 60 60 1000)
   :weeks   :week})

(defprotocol AsPeriod
  "Ultimately, return a positive number of milliseconds or nil"
  (as-period [x]))

(extend-type nil
  AsPeriod
  (as-period [_]
    nil))

(extend-type java.lang.Long
  AsPeriod
  (as-period [x]
    (when (< 0 x) x)))

(extend-type clojure.lang.Keyword
  AsPeriod
  (as-period [x]
    (let [period (x period-aliases)]
      (cond
        (nil? period) (throw (IllegalArgumentException.
                               (format "%s is not a valid period alias. Valid choices: %s"
                                 x (keys period-aliases))))
        (keyword? period) (as-period period)
        :default period))))

(extend-type clojure.lang.Sequential
  AsPeriod
  (as-period [col]
    (->> col
      (partition 2)
      (map (fn [[x p]] (* x (as-period p))))
      (reduce +))))
