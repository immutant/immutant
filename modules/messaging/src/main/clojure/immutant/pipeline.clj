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

(ns immutant.pipeline
  (:use [immutant.util :only (mapply app-name)])
  (:require [clojure.tools.logging :as log]
            [immutant.messaging    :as msg]))

(def ^:dynamic *pipeline*
  "The currently active pipeline fn. Will be bound within the
   pipeline steps and error handlers."
  nil)

(def ^:dynamic *current-step*
  "The name of the current pipeline step. Will be bound within the
   pipeline steps and error handlers."
  nil)
(def ^:dynamic *next-step*
  "The name of the next pipeline step. Will be bound within the
   pipeline steps and error handlers."
  nil)

(def ^:private pipelines (atom #{}))

(defn- wrap-error-handler [f {:keys [error-handler]}]
  (if error-handler
    (fn [m]
      (try
        (f m)
        (catch Exception e
          (error-handler e m))))
    f))

(defn- wrap-step-bindings [f current next]
  (binding [*current-step* current
            *next-step* next]
    (bound-fn* f)))

(defn- pipeline-listen [pl opts f]
  (let [{:keys [step next-step]} (meta f)
        opts (-> opts
                 (merge (meta f))
                 (assoc :selector
                   (str "step = '" step "'")))
        wrapped-f (-> f
                      (wrap-error-handler opts)
                      (wrap-step-bindings step next-step))]
    (mapply msg/listen
            pl
            (if next-step
              #(msg/publish pl (wrapped-f %)
                            :properties {"step" next-step})
              wrapped-f)            
            opts)))

(defn- pipeline-fn [pl entry]
  (vary-meta
    (fn [m & {:keys [step] :or {step entry}}]
      (msg/publish pl m :properties {"step" step}))
    assoc
    :pipeline pl))

(defn- named-steps [fns]
  (let [step-names (-> (map-indexed (fn [n s]
                                      (str (or (:name (meta s)) n))) fns)
                       vec
                       (conj nil))]
    (map
     (fn [f [step next-step]]
       (vary-meta f assoc :step step :next-step next-step))
     fns (partition 2 1 step-names))))

(defn pipeline
  "Creates a pipeline function. A pipeline is a sequence of chained
  functions (\"steps\"), each receiving the result of the previous step."
  [name & args]
  (let [steps (named-steps (take-while fn? args))
        {:as opts} (seq (drop-while fn? args)) ;; seq is a workaround for CLJ-1140
        pl (str "queue." (app-name)  ".pipeline-" name)
        pl-fn (pipeline-fn pl (-> steps first meta :step))]
    (if (some #{pl} @pipelines)
      (throw (IllegalArgumentException.
              (str "A pipeline named " name " already exists."))))
    (mapply msg/start pl opts)
    (binding [*pipeline* pl-fn]
      (let [listeners (->> steps
                           (map (partial pipeline-listen pl opts))
                           doall)]
        (swap! pipelines conj pl)
        (vary-meta pl-fn assoc :listeners listeners)))))

(defn step
  "Creates a step with additional options. {{what options?}}"
  [f & {:as opts}]
  (vary-meta f merge opts))

(defn stop
  "Shuts down the given pipeline."
  [pl & args]
  (let [{:keys [pipeline listeners]} (meta pl)]
    (swap! pipelines disj pipeline)
    (doseq [l listeners]
      (msg/unlisten l))
    (apply msg/stop pipeline args)))
