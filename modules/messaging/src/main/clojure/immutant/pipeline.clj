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

(ns immutant.pipeline
  "Provides functions for creating and managing pipelines. A pipeline
   is a composition of functions (\"steps\"), where each function is
   passed the result of the previous function. It is built on top of
   Immutant's messaging subsystem, allowing each step to have multiple
   processing threads, and to be automatically load balanced across a
   cluster.

   The `pipeline` function takes a unique (within the scope of the
   application) name, one or more single-arity functions, and optional
   kwarg options, returning a function that places its argument onto
   the pipeline when called.

   Each function can be optionally be wrapped with metadata that
   provides options for how that particular function is handled (see
   the 'step' fn below).

   Example:

   (require '[immutant.pipeline :as pl])
 
   (defn calculate-foo [m]
     ...
     (assoc m :foo value))
 
   (defn save-data [m]
     ...)
 
   ;; create a pipeline
   (defonce foo-pipeline
     (pl/pipeline \"foo\" ;; pipelines must be named
       (pl/step calculate-foo :concurrency 5) ;; run this step with 5 threads
       (pl/step #(update-in % [:bar] + (:foo %)) :name :update-bar) ;; give this step a name
       save-data ;; a 'vanilla' step
       :concurrency 2 ;; run all steps with 2 threads (unless overridden)
       :error-handler (fn [ex m] ;; do something special with errors, and retry
                        ...
                        (pl/*pipeline* m :step *current-step*))))
 
   ;; put data onto the front pipeline
   (foo-pipeline {:bar 1 :ham \"biscuit\"})
 
   ;; put data onto the pipeline at a given step
   (foo-pipeline {:bar 1 :foo 42 :ham \"gravy\"} :step :update-bar)
 
   ;; optional - it will automatically be stopped on undeploy
   (pl/stop foo-pipeline)
 
   This API is alpha, and subject to change."
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

(def halt
  "Halts pipeline processing for a given message if returned from any
   handler function."
  ::halt)

(def ^:private pipelines
  "Stores the currently active pipelines"
  (atom #{}))

(defn- wrap-error-handler
  "Wraps the given function in an error handler if one is specified in
  the options."
  [f {:keys [error-handler]}]
  (if error-handler
    (fn [m]
      (try
        (f m)
        (catch Exception e
          (error-handler e m))))
    f))

(defn- wrap-step-bindings
  [f current next]
  (binding [*current-step* current
            *next-step* next]
    (bound-fn* f)))

(defn- pipeline-listen
  "Creates a listener on the pipeline for the given function."
  [pl opts f]
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
              #(let [m (wrapped-f %)]
                 (when-not (= halt m)
                   (msg/publish pl m
                                :properties {"step" next-step})))
              wrapped-f)            
            opts)))

(defn- pipeline-fn
  "Creates a fn that places it's first arg onto the pipeline,
  optionally at a step specified by :step"
  [pl entry]
  (vary-meta
   (fn [m & {:keys [step] :or {step entry}}]
     (msg/publish pl m :properties {"step" step}))
   assoc
   :pipeline pl))

(defn- named-steps
  "Associates step names with each step function via metadata."
  [fns]
  (let [step-names (-> (map-indexed (fn [n s]
                                      (str (or (:name (meta s)) n))) fns)
                       vec
                       (conj nil))]
    (map
     (fn [f [step next-step]]
       (vary-meta f assoc :step step :next-step next-step))
     fns (partition 2 1 step-names))))

(defn pipeline
  "Creates a pipeline function.

   It takes a unique (within the scope of the application) name, one
   or more single-arity functions, and optional kwarg options, and
   returns a function that places its argument onto the pipeline when
   called.

   The following kwarg options are supported, and must follow the step
   functions [default]:

   :error-handler  a function that will be called when any step raises
                   an exception. It will be passed the exception and
                   the argument to the step. Without an error-handler,
                   the default HornetQ retry semantics will be
                   used. Can be overridden on a per-step basis - see
                   the 'step' function. [nil]
   :concurrency    the number of threads to use for *each* step. Can be
                   overridden on a per-step basis - see the 'step'
                   function. [1]

   During the execution of each step and each error-handler call, the
   following vars are bound:

   *pipeline*      the pipeline (as a fn) that is being executed
   *current-step*  the name of the currently executing step
   *next-step*     the name of the next step in the pipeline

   This function is *not* idempotent. Attempting to create a pipeline
   with the same name as an existing pipeline will raise an error."
  [name & args]
  (let [steps (named-steps (take-while fn? args))
        opts (apply hash-map (drop-while fn? args)) 
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
  "Wraps the given function with the given options, returning a function.

   The following options are supported [default]:

   :name           a name to use for the step [the current index of the fn]
   :concurrency    the number of threads to use, overriding the pipeline
                   setting. See the docs for 'pipeline' [1]
   :error-handler  an error handler function that can override the
                   pipeline setting. See the docs for 'pipeline' [nil]"
  [f & {:as opts}]
  (vary-meta f merge opts))

(defn stop
  "Destroys a pipeline. Typically not necessary since it will be done
   for you when your app is undeployed. This will fail with a warning
   if any messages are yet to be delivered unless ':force true' is
   passed. Returns true on success."
  [pl & args]
  (let [{:keys [pipeline listeners]} (meta pl)]
    (swap! pipelines disj pipeline)
    (doseq [l listeners]
      (msg/unlisten l))
    (apply msg/stop pipeline args)))
