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

(ns immutant.scheduling
  "Schedule jobs for execution"
  (:require [immutant.scheduling.internal :refer :all]
            [immutant.internal.options    :as o]
            [immutant.internal.util       :as iu]
            [immutant.util                :as u]
            [immutant.scheduling.options  :refer [resolve-options defoption]])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(defn schedule
  "Schedules a function to run according to a specification composed from any of the following:

  * [[in]] - a period after which f will be called
  * [[at]] - a time after which f will be called
  * [[every]] - the period between calls
  * [[until]] - stops the calls at a specific time
  * [[limit]] - limits the calls to a specific count
  * [[cron]] - calls f according to a [Quartz-style](http://quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/tutorial-lesson-06) cron spec

  Units for periods are milliseconds, but can also be represented as a
  keyword or a sequence of multiplier/keyword pairs,
  e.g. `[1 :week, 4 :days, 2 :hours, 30 :minutes, 59 :seconds]`.
  See [[every]] for the list of valid period keywords.

  Time values can be a `java.util.Date`, a long denoting
  milliseconds-since-epoch, or a String in `HH:mm` format, interpreted
  as the next occurence of \"HH:mm:00\" in the currently active
  timezone.

  For example:

  ```
  (schedule #(prn 'fire!)
    (-> (in 5 :minutes)
      (every 2 :hours, 30 :minutes)
      (until \"1730\")))

  (schedule #(prn 'fire!)
    (-> (at some-date)
      (every :second)
      (limit 60)))
  ```

  Ultimately, the spec is just a map, and the syntactic sugar
  functions are just assoc'ing keys corresponding to their names. The
  map can be passed either explicitly or via keyword arguments. All of
  the following are equivalent:

  ```
  (schedule #(prn 'fire!) (-> (in 5 :minutes) (every :day)))
  (schedule #(prn 'fire!) {:in [5 :minutes], :every :day})
  (schedule #(prn 'fire!) :in [5 :minutes], :every :day)
  ```

  Three additional options may be passed in the spec:

  * :id - a unique identifier for the scheduled job
  * :singleton - denotes the job's behavior in a cluster. If truthy, an :id as required. The
    given value will be used as the :id if it is truthy but not `true` and :id isn't specified [false]
  * :allow-concurrent-exec? - a boolean denoting the job's concurrency behavior in the current process [true]

  If called with an :id that has already been scheduled, the prior job
  will be replaced. If an id is not provided, a UUID is used instead.

  If :allow-concurrent-exec? is true, a job that fires while a
  previous invocation is running will also run, assuming a worker
  thread is available.

  The return value is a map of the options with any missing defaults
  filled in, including a generated id if necessary.

  You can pass additional options that will be passed on to the scheduler.
  Currently, the only scheduler option is:

  * :num-threads Specifies the number of worker threads for the scheduler's
                 thread pool [5]

  The scheduler options also define which scheduler to use, so if you
  don't pass any with a `schedule` call, you'll get the default
  scheduler configured with the default options. If you pass scheduler
  options on a subsequent call, you will get a different scheduler
  configured with those options. The same scheduler will be used for any
  future `schedule` calls with those same scheduler options.

  If you need to capture any bindings in effect when the job is
  scheduled, wrap `f` in a call to `bound-fn`."
  [f & spec]
  (let [opts (->> spec
               iu/kwargs-or-map->map
               resolve-options
               (merge create-defaults schedule-defaults))
        singleton (:singleton opts)
        id (:id opts (when (and singleton (not (true? singleton)))
                       singleton))
        final-id (or id (iu/uuid))
        scheduler (scheduler (o/validate-options opts schedule))]
    (when (and (u/in-cluster?)
            singleton
            (not id))
      (iu/warn "Singleton job scheduled in a cluster without an :id - job won't really be a singleton. See docs for immutant.scheduling/schedule."))
    (.schedule scheduler (name final-id) f
      (o/extract-options (update-in opts [:singleton] boolean)
        Scheduling$ScheduleOption))
    (-> opts
      (update-in [:ids scheduler] conj final-id)
      (assoc :id final-id))))

(o/set-valid-options! schedule
  (-> (o/opts->set Scheduling$ScheduleOption Scheduling$CreateOption)
    (conj :id :ids)
    (o/boolify :allow-concurrent-exec)))

(defn stop
  "Unschedule a scheduled job.

  Options can be passed as either a map or kwargs, but is typically
  the map returned from a [[schedule]] call. If no options are passed,
  all jobs for the default scheduler are stopped. If there are no jobs
  remaining on the scheduler the scheduler itself is stopped. Returns
  true if a job was actually removed."
  [& options]
  (let [options (-> options
                  iu/kwargs-or-map->map
                  (o/validate-options schedule "stop"))
        ids (if-let [ids (:ids options)]
              ids
              (let [s (scheduler options)]
                {s (if-let [id (:id options)]
                     [id]
                     (.scheduledJobs s))}))
        stopped? (some boolean
                   (doall (for [[^Scheduling s ids] ids
                                id ids]
                            (.unschedule s (name id)))))]
    (doseq [^Scheduling scheduler (keys ids)]
      (when (empty? (.scheduledJobs scheduler))
        (.stop scheduler)))
    stopped?))

(defoption ^{:arglists '([n] [kw] [m v] [n kw & n-kws] [m n kw & n-kws])} in
  "Specifies the period after which the job will fire, in
  milliseconds, a period keyword, or multiplier/keyword pairs, e.g.
  `(in 5 :minutes 30 :seconds)`. See [[every]] for the list of valid
   period keywords. See [[schedule]].")

(defoption ^{:arglists '([date] [ms] [HHmm] [m v])} at
  "Takes a time after which the job will fire, so it will run
  immediately if the time is in the past; can be a `java.util.Date`,
  millis-since-epoch, or a String in `HH:mm` format. See
  [[schedule]].")

(defoption ^{:arglists '([n] [kw] [m v] [n kw & n-kws] [m n kw & n-kws])} every
  "Specifies a period between function calls, in milliseconds, a
  period keyword, or multiplier/keyword pairs, e.g.
  `(every 1 :hour 20 :minutes)`.  Both singular and plural versions of
  :second, :minute, :hour, :day, and :week are valid period keywords.
  See [[schedule]].")

(defoption ^{:arglists '([date] [ms] [HHmm] [m v])} until
  "When [[every]] is specified, this limits the invocations by time;
  can be a `java.util.Date`, millis-since-epoch, or a String in
  `HH:mm` format, e.g. `(-> (every :hour) (until \"17:00\"))`. When
  combined with [[limit]], whichever triggers first ends the
  iteration. See [[schedule]].")

(defoption ^{:arglists '([n] [m n])} limit
  "When [[every]] is specified, this limits the invocations by count,
  including the first one, e.g. `(-> (every :hour) (limit 10))`. When
  combined with [[until]], whichever triggers first ends the
  iteration. See [[schedule]].")

(defoption ^{:arglists '([str] [m str])} cron
  "Takes a Quartz-style cron spec, e.g. `(cron \"0 0 12 ? * WED\")`,
   see the [Quartz docs](http://quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/tutorial-lesson-06)
   for the syntax. See [[schedule]].")

(defoption ^{:arglists '([boolean-or-id] [m boolean-or-id])} singleton
  "If truthy (false by default), only one instance of a given job name
   will run in a cluster. If truthy, an :id as required. The given
   value will be used as the :id if it is truthy but not `true`
   and :id isn't specified. See [[schedule]].")

(defoption ^{:arglists '([boolean] [m boolean])} allow-concurrent-exec?
  "If true (the default), the job is allowed to run concurrently
   within the current process. If false it will be forced to run
   sequentially. To disallow concurrent execution cluster wide make
   sure to also set the :singleton option. See [[singleton]].")

(defoption ^{:arglists '([str] [m str])} id
  "Takes a String or keyword to use as the unique id for the job. See [[schedule]].")
