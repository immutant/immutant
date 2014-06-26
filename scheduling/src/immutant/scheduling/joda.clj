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

(ns immutant.scheduling.joda
  "Adapts the [clj-time](https://github.com/clj-time/clj-time)
  library, which must be included among your app's dependencies to
  load this namespace"
  (:require [immutant.coercions]
            [immutant.scheduling    :as i]
            [immutant.internal.util :as u])
  (:import (org.joda.time DateTime)))

(extend-type DateTime
  immutant.coercions/AsTime
  (as-time [x] (.toDate x)))

(defn schedule-seq
  "Lazily schedule a job for each `DateTime` in a sequence, typically
  returned from `clj-time.periodic/periodic-seq`. For any two
  successive elements, the second is scheduled upon completion of the
  first, and they will all have the same id."
  [job seq]
  (let [id (u/uuid)]
    (letfn [(f [t & ts]
              (i/schedule #(do (job) (if ts (apply f ts)))
                {:id id, :at t}))]
      (apply f seq))))
