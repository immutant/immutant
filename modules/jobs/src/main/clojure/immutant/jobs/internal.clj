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

(ns ^{:no-doc true} immutant.jobs.internal
  (:use [immutant.util :only [app-name]])
  (:require [immutant.registry :as registry]
            [clojure.tools.logging :as log])
  (:import java.util.Date))



(defn ^{:private true} wait-for
  "Waits for the item to start before invoking f"
  ([item]
     (wait-for item (constantly item)))
  ([item f]
     (wait-for item f 300))
  ([item f attempts]
     (cond
      (.isStarted item) (f)
      (< attempts 0)         (throw (IllegalStateException.
                                     (str "Gave up waiting for " (.getName item) " to start")))
      :default               (do
                               (log/debug "Waiting for" (.getName item) "to start")
                               (Thread/sleep 100)
                               (recur item f (dec attempts))))))

(defn ^:internal job-schedulizer []
  (wait-for (registry/get "job-schedulizer")))

(defn ^{:internal true} scheduler
  "Retrieves the appropriate scheduler, starting it if necessary"
  []
  (wait-for (.activateScheduler (job-schedulizer))))

(defn ^:internal quartz-scheduler
  "Returns the internal quartz scheduler"
  []
  (.getScheduler (scheduler)))

(defn ^:internal date
  "A wrapper around Date. to facilitate testing"
  [ms]
  (Date. ms))

;; TODO: use defmulti/protocol?
(defn ^:internal as-date
  [ms-or-date]
  (cond
   (instance? Date ms-or-date)       ms-or-date
   (and ms-or-date (< 0 ms-or-date)) (date ms-or-date)))

(defn ^:private create-scheduled-job [f name spec singleton]
  (.createJob (job-schedulizer) f name spec (boolean singleton)))

(defn ^:private create-at-job [f name {:keys [after at every in repeat until]} singleton]
  (and at in
       (throw (IllegalArgumentException. "You can't specify both :at and :in")))
  (and repeat (not every)
       (throw (IllegalArgumentException. "You can't specify :repeat without :every")))
  (and until (not every)
       (throw (IllegalArgumentException. "You can't specify :until without :every")))
  
  (.createAtJob (job-schedulizer)
                f
                name
                (or (as-date at)
                    (and in (< 0 in)
                         (as-date (+ in (System/currentTimeMillis)))))
                (as-date until)
                (or every 0)
                (or repeat 0)
                (boolean singleton)))

(defn ^{:internal true} create-job
  "Instantiates and starts a job"
  [f name spec singleton]
  ((if (map? spec)
     create-at-job
     create-scheduled-job) f name spec singleton))

(defn ^{:internal true} stop-job
  "Stops (unschedules) a job, removing it from the scheduler."
  [job]
  (if (.isStarted job)
    (.stop job)))


(defmulti extract-spec #(class (fnext %)))

(let [at-keys [:at :in :every :repeat :until]]
  (defn ^:private throw-when-at-opts [opts]
    (when (some (set at-keys) opts)
      (throw (IllegalArgumentException.
              "You can't specify a cron spec and 'at' options."))))
  
  (defmethod extract-spec clojure.lang.Fn [opts]
    (log/warn "Supplying the cronspec before the fn is deprecated;"
              "provide the cronspec after the fn argument.")
    (throw-when-at-opts opts)
    (assoc (apply hash-map (nnext opts))
      :spec (first opts)
      :fn (fnext opts)))

  (defmethod extract-spec String [opts]
    (throw-when-at-opts opts)
    (assoc (apply hash-map (nnext opts))
      :spec (fnext opts)
      :fn (first opts)))

  (defmethod extract-spec :default [opts]
    (let [m (apply hash-map (rest opts))]
      (assoc (apply dissoc m at-keys)
        :spec (select-keys m at-keys)
        :fn (first opts)))))

