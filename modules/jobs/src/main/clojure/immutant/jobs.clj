;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
  (:use [immutant.utilities :only [at-exit]])
  (:require [clojure.tools.logging :as log]
            [immutant.jobs.internal :as internal]))

(def ^:dynamic ^org.quartz.JobExecutionContext *job-execution-context* nil)

(def ^{:private true} current-jobs (atom {}))

(defn unschedule
  "Removes the named job from the scheduler"
  [name]
  (when-let [job (@current-jobs name)]
    (log/info "Unscheduling job" name)
    (internal/stop-job job)
    (swap! current-jobs dissoc name)
    true))

(defn schedule
  "Schedules a job to execute based on the spec.
Calling this function with the same name as a previously scheduled job will replace that job."
  [name spec f & {singleton :singleton :or {singleton true}}]
  (unschedule name)
  (log/info "Scheduling job" name "at" spec)
  (letfn [(job [ctx] (binding [*job-execution-context* ctx] (f)))]
    (swap! current-jobs assoc name
           (internal/create-job job name spec (boolean singleton))))
  (at-exit (partial unschedule name))
  nil)

(defn internal-scheduler
  "Returns the internal Quartz scheduler for use with other libs, e.g. Quartzite"
  [& {:keys [singleton] :or {singleton true}}]
  (internal/quartz-scheduler singleton))