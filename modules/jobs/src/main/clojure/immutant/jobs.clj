;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.jobs
  "Associate recurring jobs with an application using cron-like specifications"
  (:use [immutant.util :only [at-exit]])
  (:require [clojure.tools.logging :as log]
            [immutant.jobs.internal :as internal]))

(def ^:dynamic *job-execution-context* nil)

(def ^{:private true} current-jobs (atom {}))

(defn unschedule
  "Removes the named job from the scheduler"
  [name]
  (when-let [job (@current-jobs name)]
    (log/info "Unscheduling job" name)
    (internal/stop-job job)
    (swap! current-jobs dissoc name)
    true))

(def
  ^{:arglists '([name f spec & {:keys [singleton] :or {singleton true}}]
                  [name f & {:keys [at in until every repeat singleton] :or {singleton true}}])
    :doc "Schedules a job to execute based on the cron spec or the 'at' options.

Available options [default]:
  :at        Specifies when the 'at' job should start firing. Can be a
             java.util.Date or ms since epoch. Can't be specified with
             a spec or :in [none, now if no spec provided]
  :in        Specifies when the 'at' job should start firing, in ms from
             now. Can't be specified with a spec or :at [none]
  :every     Specifies the delay interval between
             'at' job firings, in ms. If specified without a :repeat
             or :until, the job will fire indefinitely. Can't be
             specified with a spec [none]
  :repeat    Specifies the number of times an 'at' job should repeat
             beyond its initial firing. Can't be specified with a
             spec, and requires :every to be provided [none]
  :until     Specifies when the 'at' job should stop firing. Can be a
             java.util.Date or ms since epoch. Can't be specified with
             a spec [none]
  :singleton Marks the job as a singleton in a cluster. Singleton
             jobs will only execute on one node. If false, the job will
             execute on every node [true]

Calling this function with the same name as a previously scheduled job
will replace that job."}
  schedule
  (fn
    [name & opts]
    (let [{:keys [fn spec] :as opts} (internal/extract-spec opts)]
      (unschedule name)
      (log/info "Scheduling job" name "with" spec)
      (letfn [(job [ctx] (binding [*job-execution-context* ctx] (fn)))]
        (swap! current-jobs assoc name
               (internal/create-job job name spec (:singleton opts true)))))
    nil))

(defn internal-scheduler
  "Returns the internal Quartz scheduler for use with other libs, e.g. Quartzite"
  [& {:keys [singleton] :or {singleton true}}]
  (internal/quartz-scheduler singleton))
