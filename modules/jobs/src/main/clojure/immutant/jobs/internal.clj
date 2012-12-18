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

(ns ^{:no-doc true} immutant.jobs.internal
  (:use [immutant.util :only [app-name]])
  (:require [immutant.registry :as registry]
            [clojure.tools.logging :as log]))

(defn ^{:private true} job-schedulizer  []
  (registry/get "job-schedulizer"))

(defn ^{:internal true} create-scheduler
  "Creates a scheduler for the current application.
A singleton scheduler will participate in a cluster, and will only execute its jobs on one node."
  [singleton]
  (log/info "Creating job scheduler for"  (app-name) "singleton:" singleton)
  (.createScheduler (job-schedulizer) singleton))

(defn ^{:internal true} scheduler
  "Retrieves the appropriate scheduler, creating it if necessary"
  [singleton]
  (let [name (str (if singleton "singleton-" "") "job-scheduler")]
    (if-let [scheduler (registry/get name)]
      scheduler
      (registry/put name (create-scheduler singleton)))))

(defn ^{:private true} wait-for-scheduler
  "Waits for the scheduler to start before invoking f"
  ([scheduler f]
     (wait-for-scheduler scheduler f 30))
  ([scheduler f attempts]
     (cond
      (.isStarted scheduler) (f)
      (< attempts 0)         (throw (IllegalStateException.
                                     (str "Gave up waiting for " (.getName scheduler) " to start")))
      :default               (do
                               (log/debug "Waiting for scheduler" (.getName scheduler) "to start")
                               (Thread/sleep 1000)
                               (recur scheduler f (dec attempts))))))

(defn ^:internal quartz-scheduler
  "Returns the internal quartz scheduler"
  [singleton]
  (let [s (scheduler singleton)]
    (wait-for-scheduler s #(.getScheduler s))))

(defn ^{:internal true} create-job
  "Instantiates and starts a job"
  [f name spec singleton]
  (scheduler singleton) ;; creates the scheduler if necessary
  (.createJob (job-schedulizer) f name spec singleton))

(defn ^{:internal true} stop-job
  "Stops (unschedules) a job, removing it from the scheduler."
  [job]
  (if (.isStarted job)
    (.stop job)))


