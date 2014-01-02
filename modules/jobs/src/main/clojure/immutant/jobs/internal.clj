;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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
  (:use [immutant.util :only [app-name wait-for-start]])
  (:require [immutant.registry :as registry]
            [clojure.tools.logging :as log])
  (:import (java.util Calendar Date)))

(defn ^:internal job-schedulizer []
  (wait-for-start (registry/get "job-schedulizer")))

(defn ^{:internal true} scheduler
  "Retrieves the appropriate scheduler, starting it if necessary"
  []
  (wait-for-start (.activateScheduler (job-schedulizer))))

(defn ^:internal quartz-scheduler
  "Returns the internal quartz scheduler"
  []
  (.getScheduler (scheduler)))

(defn ^:internal date
  "A wrapper around Date. to facilitate testing"
  [ms]
  (Date. ms))

(defn ^:internal now->millis
  "A wrapper around System/currentTimeMillis to facilitate testing"
  []
  (System/currentTimeMillis))

(defn ^:internal now->calendar
  "A wrapper around Calendar/getInstance to facilitate testing"
  []
  (Calendar/getInstance))

(def period-aliases
  {:second  1000
   :seconds :second
   :minute  (* 60 1000)
   :minutes :minute
   :hour    (* 60 60 1000)
   :hours   :hour
   :day     (* 24 60 60 1000)
   :days    :day
   :week    (* 7 24 60 60 1000)
   :weeks   :week})

(defprotocol AsPeriod
  (as-period [x]))

(extend-type nil
  AsPeriod
  (as-period [_]
    nil))

(extend-type java.lang.Long
  AsPeriod
  (as-period [x]
    (when (< 0 x) x)))

(extend-type clojure.lang.Keyword
  AsPeriod
  (as-period [x]
    (let [period (x period-aliases)]
      (cond
       (nil? period) (throw
                      (IllegalArgumentException.
                       (format
                        "%s is not a valid period alias. Valid choices are: %s"
                        x (keys period-aliases))))
       (keyword? period) (as-period period)
       :default period))))

(extend-type clojure.lang.Sequential
  AsPeriod
  (as-period [[n alias]]
    (* n (as-period alias))))

(defn- next-occurrence-of-time [hour min]
  (let [now (now->calendar)
        then (doto (now->calendar)
               (.set Calendar/HOUR_OF_DAY hour)
               (.set Calendar/MINUTE      min)
               (.set Calendar/SECOND      0))]
    (when (> 0 (.compareTo then now))
      (.add then Calendar/DAY_OF_YEAR 1))
    (.getTime then)))

(defprotocol AsDate
  (as-date [x]))

(extend-type nil
  AsDate
  (as-date [_] nil))

(extend-type Date
  AsDate
  (as-date [x] x))

(extend-type java.lang.Long
  AsDate
  (as-date [x]
    (when (< 0 x) (date x))))

(extend-type String
  AsDate
  (as-date [x]
    (if-let [match (re-find #"(\d\d):?(\d\d)" x)]
      (->> (rest match)
           (map #(Integer/parseInt %))
           (apply next-occurrence-of-time))
      (throw (IllegalArgumentException.
              (format
               "%s is not a valid time specification. Valid specifications are: HH:MM, HHMM"
               x))))))

(defn ^:private create-scheduled-job [f name spec singleton]
  (.createJob (job-schedulizer) f name spec (boolean singleton)))

(defn ^:private create-at-job [f name {:keys [after at every in repeat until]} singleton]
  (and at in
       (throw (IllegalArgumentException. "You can't specify both :at and :in")))
  (and repeat (not every)
       (throw (IllegalArgumentException. "You can't specify :repeat without :every")))
  (and until (not every)
       (throw (IllegalArgumentException. "You can't specify :until without :every")))
  
  (.createAtJob
   (job-schedulizer)
   f
   name
   (or (as-date at)
       (if-let [in (as-period in)]
         (as-date (+ in (now->millis)))))
   (as-date until)
   (if-let [every (as-period every)]
     every
     0)
   (or repeat 0)
   (boolean singleton)))

(defn ^{:internal true} create-job
  "Instantiates and starts a job"
  [f name spec singleton]
  ((if (map? spec)
     create-at-job
     create-scheduled-job) f name spec singleton))

(defn ^{:internal true} kill-job
  "Kills (unschedules) a job, removing it from the scheduler. A dead
  job cannot be restarted."
  [job]
  (.kill job))

(defmulti extract-spec #(class (fnext %)))

(let [at-keys [:at :in :every :repeat :until]
      throw-when-at-opts
      (fn [opts]
        (when (some (set at-keys) opts)
          (throw (IllegalArgumentException.
                  "You can't specify a cron spec and 'at' options."))))]
  
  (defmethod extract-spec clojure.lang.Fn [opts]
    (throw (IllegalArgumentException.
            (str "Supplying the cronspec before the fn is no longer supported; "
                 "provide the cronspec after the fn argument."))))

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

