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
  (:require [immutant.scheduling.internal :refer :all]
            [immutant.internal.options    :refer :all]
            [immutant.internal.util       :as u]
            [immutant.scheduling.options  :refer [resolve-options defoption]]
            [clojure.walk                 :refer [keywordize-keys]])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(defn schedule
  "Schedules a function to execute according to a specification map.

  Option helper functions (defined below) can be combined to create the
  spec, e.g.

  ```
  (schedule
    #(println \"I'm running!\")
      (-> (id :some-job)
        (in 5 :minutes)
        (every 2 :hours, 30 :minutes)
        (until \"1730\")))
  ```

  The above is the same as:

  ```
  (schedule
    #(println \"I'm running!\")
      {:id :some-job
       :in 300000
       :every 9000000
       :until a-date-representing-1730-today})
  ```

  The options can also be specified as kwargs:

  ```
  (schedule
    #(println \"I'm running!\")
    :id :some-job
    :in 300000
    :every 9000000
    :until a-date-representing-1730-today)
  ```

  If called with an id that has already been scheduled, the prior job will
  be replaced. If an id is not provided, a uuid is used instead. Returns
  the options with any missing defaults filled in, including a generated
  id if necessary.

  TODO: doc scheduler options and scheduler lookup"
  [f & options]
  (let [opts (->> options
               u/kwargs-or-map->map
               keywordize-keys
               resolve-options
               (merge create-defaults schedule-defaults))
        id (:id opts (u/uuid))
        scheduler (scheduler (validate-options opts schedule))]
    (.schedule scheduler (name id) f
      (extract-options opts Scheduling$ScheduleOption))
    (-> opts
      (update-in [:ids scheduler] conj id)
      (assoc :id id))))

(set-valid-options! schedule
  (conj (opts->set Scheduling$ScheduleOption Scheduling$CreateOption) :id :ids))

(defn stop
  "Unschedule a scheduled job.

  Options can be passed as either a map or kwargs, but is typically the
  map returned from a {{schedule}} call. If there are no jobs remaining on
  the scheduler the scheduler itself is stopped. Returns true if a job
  was actually removed."
  ([key value & key-values]
     (stop (apply hash-map key value key-values)))
  ([options]
      (let [options (-> options
                      keywordize-keys
                      (validate-options schedule "stop"))
            ids (:ids options {(scheduler options)
                               [(:id options)]})
            stopped? (some boolean (doall (for [[s ids] ids, id ids]
                                            (.unschedule s (name id)))))]
        (doseq [scheduler (keys ids)]
          (when (empty? (.scheduledJobs scheduler))
            (.stop scheduler)))
        stopped?)))

(defoption in
  "Takes a duration after which the job will fire, e.g. `(in 5 :minutes)`")

(defoption at
  "Takes a time before which the job will not fire, so it will run
  immediately if the time is in the past; can be a java.util.Date,
  millis-since-epoch, or a string in 'HH:mm' format")

(defoption every
  "Takes a delay interval between job firings, e.g. `(every 2 :hours)`")

(defoption until
  "When {{every}} is specified, limits the firings by time; can be a
  java.util.Date, millis-since-epoch, or a string in 'HH:mm' format,
  e.g. `(-> (every :hour) (until \"17:00\"))`")

(defoption limit
  "When {{every}} is specified, limits the firings by count, including the
  first one, e.g. `(-> (every :hour) (limit 10))`. When {{until}} and `limit`
  are combined, whichever triggers first ends the iteration.")

(defoption cron
  "Takes a Quartz-style cron spec, e.g. `(cron \"0 0 12 ? * WED\")`, see the
   [Quartz docs](http://quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/tutorial-lesson-06)
   for more details.")

(defoption singleton
  "Takes a boolean. If true (the default), only one instance of a given job name
   will run in a cluster.")

(defoption id
  "Takes a String or keyword to use as the id of the job.")
