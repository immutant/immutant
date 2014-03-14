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

(ns ^{:no-doc true} immutant.scheduling.options
    (:require [immutant.scheduling.dates :refer [as-date]]
              [immutant.scheduling.periods :refer [as-period]]))

(defn at [opts]
  (if-let [v (:at opts)]
    (assoc opts :at (as-date v))
    opts))

(defn until [opts]
  (if-let [v (:until opts)]
    (assoc opts :until (as-date v))
    opts))

(defn every [opts]
  (if-let [v (:every opts)]
    (assoc opts :every (as-period v))
    opts))

(defn in [opts]
  (if-let [v (:in opts)]
    (assoc opts :in (as-period v))
    opts))

(def resolve-options (comp at until every in))
