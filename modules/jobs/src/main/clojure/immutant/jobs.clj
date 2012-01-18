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
  (:use immutant.utilities
        immutant.jobs.core)
  (:require [clojure.tools.logging :as log]))

(def ^{:private true} current-jobs (atom {}))

(defn unschedule
  "Removes the named job from the scheduler"
  [name]
  (when-let [job (@current-jobs name)]
    (log/info "Unscheduling job" name)
    (stop-job job)
    (swap! current-jobs dissoc name)))

(defn schedule
  "Schedules a job to execute based on the spec.
Calling this function with the same name as a previously scheduled job will replace that job."
  [name spec f & options]
  (unschedule name)
  (log/info "Scheduling job" name "at" spec)
  (swap! current-jobs assoc name
         (create-job f name spec (boolean (:singleton (apply hash-map options)))))
  (at-exit (partial unschedule name)))
