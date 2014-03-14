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

(ns ^{:no-doc true} immutant.scheduling.dates
    (:import (java.util Calendar Date)))

(defn ^:internal calendar
  "A wrapper around Calendar/getInstance to facilitate testing"
  []
  (Calendar/getInstance))

(defn- next-occurrence-of-time [hour min]
  (let [now (calendar)
        then (doto (calendar)
               (.set Calendar/HOUR_OF_DAY hour)
               (.set Calendar/MINUTE      min)
               (.set Calendar/SECOND      0))]
    (when (> 0 (.compareTo then now))
      (.add then Calendar/DAY_OF_YEAR 1))
    (.getTime then)))

(defprotocol AsDate
  (as-date [x]))

(extend-type nil
  AsDate
  (as-date [_] nil))

(extend-type Date
  AsDate
  (as-date [x] x))

(extend-type java.lang.Long
  AsDate
  (as-date [x]
    (when (< 0 x) (Date. x))))

(extend-type String
  AsDate
  (as-date [x]
    (if-let [match (re-find #"(\d\d):?(\d\d)" x)]
      (->> (rest match)
           (map #(Integer/parseInt %))
           (apply next-occurrence-of-time))
      (throw (IllegalArgumentException.
              (format
               "%s is not a valid time specification. Valid specifications: HH:mm, HHmm"
               x))))))
