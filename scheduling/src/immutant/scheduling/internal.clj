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

(ns ^:no-doc ^:internal immutant.scheduling.internal
    (:require [immutant.internal.options :refer :all]
              [immutant.internal.util    :as u])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(def ^:internal create-defaults (opts->defaults-map Scheduling$CreateOption))
(def ^:internal schedule-defaults (opts->defaults-map Scheduling$ScheduleOption))

(def scheduler-name
  (partial u/hash-based-component-name create-defaults))

(defn ^Scheduling scheduler [opts]
  (WunderBoss/findOrCreateComponent Scheduling
    (scheduler-name (select-keys opts (valid-options-for scheduler)))
    (extract-options opts Scheduling$CreateOption)))
