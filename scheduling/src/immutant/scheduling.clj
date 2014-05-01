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
  (:require [immutant.opts-validation    :refer [extract-options opts->set
                                                 set-valid-options! validate-options]]
            [immutant.scheduling.options :refer [resolve-options defoption]])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(defn scheduler
  "Create a scheduler or return existing one matching :name (defaults to \"default\").
   Any options here are applied to the scheduler with the given name,
   but only if it has not yet been instantiated."
  [& {:as opts}]
  (let [opts (-> opts
               keywordize-keys
               (validate-options scheduler))]
    (WunderBoss/findOrCreateComponent Scheduling
      (:name opts)
      (extract-options opts Scheduling$CreateOption))))

(set-valid-options! scheduler
  (conj (opts->set Scheduling$CreateOption) :name))

(defn schedule
  "Schedules a function to execute according to a specification map.
  Option functions (defined below) can be combined to create the
  spec, e.g.

    (schedule id f
      (-> (in 5 :minutes)
        (every 2 :hours, 30 :minutes)
        (until \"1730\")))

  All jobs must have a unique id, used to unschedule or, when schedule
  is called with the same id, to reschedule jobs."
  ([id f spec] (schedule (scheduler) id f spec))
  ([scheduler id f spec]
     (let [opts (-> spec
                  keywordize-keys
                  resolve-options
                  (validate-options schedule))]
       (.schedule scheduler (name id) f
         (extract-options opts Scheduling$ScheduleOption)))))

(set-valid-options! schedule
  (opts->set Scheduling$ScheduleOption))

(defn unschedule
  "Unschedule a job given its id"
  ([id] (unschedule (scheduler) id))
  ([scheduler id]
     (.unschedule scheduler (name id))))

(defoption in
  "Takes a duration after which the job will fire, e.g. (in 5 :minutes)")

(defoption at
  "Takes a time denoting when the job should fire, can be a
  java.util.Date, ms-since-epoch, or a string in HH:mm format")

(defoption every
  "Takes a delay interval between job firings, e.g. (every 2 :hours)")

(defoption until
  "When every is specified, limits the firings by time; can be a
  java.util.Date, ms-since-epoch, or a string in HH:mm format,
  e.g. (-> (every :hour) (until \"17:00\"))")

(defoption limit
  "When every is specified, limits the firings by count, including the
  first one, e.g. (-> (every :hour) (limit 10)). When until and limit
  are combined, whichever triggers first ends the iteration")

(defoption cron
  "Takes a Quartz-style cron spec, e.g. (cron \"0 0 12 ? * WED\"), see
  http://quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/tutorial-lesson-06")

(defoption singleton
  "Takes a boolean. If true (the default), only one instance of a given job name
   will run in a cluster.")
