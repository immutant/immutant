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
    (:require [immutant.scheduling.coercions :refer [as-time as-period]]))

(defn option [k f]
  (fn [opts]
    (if-let [v (k opts)]
      (assoc opts k (f v))
      opts)))

(def at (option :at #'as-time))
(def in (option :in #'as-period))

(def until (option :until #'as-time))
(def every (option :every #'as-period))

(def resolve-options (comp at until every in))

(defmacro defoption [sym doc]
  `(defn ~sym ~doc [& ~'opts]
     (let [[m# & ~'opts] (if (map? (first ~'opts))
                          ~'opts
                          (cons {} ~'opts))
           ~'opts (if (> (count ~'opts) 1)
                   ~'opts
                   (first ~'opts))]
       (assoc m# ~(keyword (name sym)) ~'opts))))
