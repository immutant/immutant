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

(ns immutant.jobs.core
  (:use immutant.core)
  (:require [immutant.registry :as registry]
            [clojure.tools.logging :as log])
  (:import [org.immutant.jobs ClojureJob ScheduledJobMBean]
           [org.projectodd.polyglot.jobs BaseScheduledJob]))

(def current-jobs (atom {}))

(defn create-scheduler [singleton]
  (log/info "Creating job scheduler for"  (app-name) "singleton:" singleton)
  (.createScheduler (registry/fetch "job-schedulizer") singleton))

(defn register-scheduler [name scheduler]
  (registry/put name scheduler)
  scheduler)

(defn scheduler [singleton]
  (let [name (str (if singleton "singleton-" "") "job-scheduler")
        scheduler (registry/fetch name)]
    (if scheduler
      scheduler
      (register-scheduler name (create-scheduler singleton)))))

(defn wait-for-scheduler
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

(defn create-job [f name spec singleton]
  (let [scheduler (scheduler singleton)]
    (doto
     (proxy [BaseScheduledJob ScheduledJobMBean]
         [ClojureJob (app-name) name "" spec singleton]
       (start []
         (wait-for-scheduler
          scheduler
          #(.addJob scheduler name (ClojureJob. f)))
         (proxy-super start))
       (getScheduler []
         (.getScheduler scheduler)))
     .start)))

(defn stop-job [job]
  (.stop job))
