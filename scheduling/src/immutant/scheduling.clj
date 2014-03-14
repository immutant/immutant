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

(ns immutant.scheduling
  "Schedule jobs for execution"
  (:require [immutant.util :as u]
            [immutant.scheduling.options :refer [resolve-options]])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(defn ^{:valid-options (conj (u/enum->set Scheduling$CreateOption) :name)}
  configure
  "Configures the default scheduler and returns it"
  [& {:as opts}]
  (let [opts (->> opts
               (merge {:name "default" :num-threads 5})
               (u/validate-options configure))]
    (WunderBoss/findOrCreateComponent Scheduling
      (:name opts)
      (u/extract-options opts Scheduling$CreateOption))))

(defn ^{:valid-options (u/enum->set Scheduling$ScheduleOption)}
  schedule
  "Schedules a job to execute"
  [scheduler id f & {:as opts}]
  (let [opts (->> opts
               resolve-options
               (u/validate-options schedule))]
    (.schedule scheduler (name id) f
      (u/extract-options opts Scheduling$ScheduleOption))))

(defn unschedule
  "Unschedule a job"
  ([id] (unschedule (configure) id))
  ([scheduler id]
     (.unschedule scheduler (name id))))
