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

(ns immutant.pipeline
  "Provides functions for creating and managing pipelines. A pipeline
   is a composition of functions (\"steps\"), where each function is
   passed the result of the previous function, dereferenced if needed.
   It is built on top of Immutant's messaging subsystem, allowing each
   step to have multiple processing threads, and to be automatically
   load balanced across a cluster.

   The `pipeline` function takes a unique (within the scope of the
   application) name, one or more single-arity functions, and optional
   kwarg options, returning a function that places its argument onto
   the pipeline when called. The resulting pipeline-fn optionally
   returns a delay that can be used to retrieve the result of the
   pipeline execution.

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

   ;; get the result
   (deref (foo-pipeline {:bar 1 :ham \"biscuit\"}) 1000 ::timeout!)

   ;; optional - it will automatically be stopped on undeploy
   (pl/stop foo-pipeline)"
  (:use [immutant.messaging.core :only [delayed]])
  (:require [clojure.tools.logging   :as log]
            [immutant.messaging      :as msg]
            [immutant.xa.transaction :as tx]
            [immutant.util           :as u])
  (:import java.util.UUID
           java.util.concurrent.TimeoutException))

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

(defn fanout
  "A function that takes a seq and places each item in it on the pipeline at the next step.
   This halts pipeline execution for the current message, but
   continues execution for each seq element. Note that a pipeline that
   uses this function cannot be correctly derefenced, since the deref
   will only get the first value to finish the pipeline."
  [xs]
  (doseq [x xs]
    (*pipeline* x :step *next-step*))
  halt)

(defonce  ^{:private true
            :doc "Stores the currently active pipelines"}
  pipelines
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

(defn- wrap-fanout [f {:keys [fanout?]}]
  (if fanout?
    (comp fanout f)
    f))

(defn- wrap-result-passing
  [f pl current-step next-step opts]
  (if next-step
    (fn [m]
      (let [timeout (:step-deref-timeout opts 60000) ;; 10s
            m (u/maybe-deref (f m) timeout ::timeout)] 
        (condp = m
          halt      nil
          ::timeout (throw (TimeoutException.
                            (format "Timed out after %d ms when dereferencing the result of step %s"
                                    timeout current-step)))
          (msg/publish pl m
                       :encoding :clojure
                       :correlation-id (.getJMSCorrelationID msg/*raw-message*)
                       :properties {"step" next-step}))))
    f))

(defn- wrap-no-tx
  [f]
  (fn [m]
    (tx/not-supported (f m))))

(defn- pipeline-listen
  "Creates a listener on the pipeline for the given function."
  [pl opts f]
  (let [{:keys [step next-step]} (meta f)
        opts (-> opts
                 (merge (meta f))
                 (assoc :selector (str "step = '" step "'")))]
    (u/mapply msg/listen pl
      (-> f
          (wrap-fanout opts)
          (wrap-result-passing pl step next-step opts)
          (wrap-error-handler opts)
          (wrap-step-bindings step next-step)
          wrap-no-tx)
      opts)))

(defn- create-delay
  [pl id keep-result?]
  (if keep-result?
    (delayed
     (fn [t]
       (u/mapply msg/receive pl
               {:timeout t
                :selector (str "JMSCorrelationID='" id "' AND result = true")})))
    (delay (throw (IllegalStateException.
                   "Attempt to derefence a pipeline that doesn't provide a result")))))

(defn- pipeline-fn
  "Creates a fn that places its first arg onto the pipeline,
  optionally at a step specified by :step"
  [pl step-names keep-result?]
  (vary-meta
   (fn [m & {:keys [step] :or {step (first step-names)}}]
     (let [step (str step)
           id (str (UUID/randomUUID))]
       (when-not (some #{step} step-names)
         (throw (IllegalArgumentException. 
                 (format "'%s' is not one of the available steps: %s" step (vec step-names)))))
       (msg/publish pl m
                    :encoding :clojure
                    :properties {"step" step}
                    :correlation-id id)
       (create-delay pl id keep-result?)))
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

(defn- sync-result-step-fn
  "Returns a function that publishes the result of the pipeline so it can be deref'ed"
  [pl ttl]
  #(msg/publish pl %
                :encoding :clojure
                :ttl ttl
                :correlation-id (.getJMSCorrelationID msg/*raw-message*)
                :properties {"result" true}))

(defn ^{:valid-options
        #{:concurrency :error-handler :result-ttl :step-deref-timeout :durable}}
  pipeline
  "Creates a pipeline function.

   It takes a unique (within the scope of the application) name, one
   or more single-arity functions, and optional kwarg options, and
   returns a function that places its argument onto the pipeline when
   called. If :result-ttl is > -1, it returns a delayed value that can
   be derefed to get the result of the pipeline execution.

   The following kwarg options are supported, and must follow the step
   functions [default]:

   :concurrency         the number of threads to use for *each* step. Can be
                        overridden on a per-step basis - see the 'step'
                        function. [1]
   :error-handler       a function that will be called when any step raises
                        an exception. It will be passed the exception and
                        the argument to the step. Without an error-handler,
                        the default HornetQ retry semantics will be
                        used. Can be overridden on a per-step basis - see
                        the 'step' function. [nil]
   :result-ttl          the time-to-live for the final pipeline result,
                        in ms. Set to 0 for \"forever\", -1 to disable
                        returning the result via a delay [1 hour]
   :step-deref-timeout  the amount of time to wait when dereferencing
                        the result of a step that returns a delay,
                        in ms. Can be overridden on a per-step basis -
                        see the 'step' function. [10 seconds]
   :durable             whether messages persist across restarts [true]

   During the execution of each step and each error-handler call, the
   following vars are bound:

   *pipeline*      the pipeline (as a fn) that is being executed
   *current-step*  the name of the currently executing step
   *next-step*     the name of the next step in the pipeline

   This function is *not* idempotent. Attempting to create a pipeline
   with the same name as an existing pipeline will raise an error."
  [pl-name & args]
  (let [opts (u/validate-options pipeline (apply hash-map (drop-while fn? args)))
        pl (str "queue." (u/app-name)  ".pipeline-" (name pl-name))
        result-ttl (:result-ttl opts 3600000) ;; 1 hr
        keep-result? (>= result-ttl 0)
        steps (-> (take-while fn? args)
                  vec
                  (#(if keep-result?
                      (conj % (sync-result-step-fn pl result-ttl))
                      %))
                  named-steps)
        pl-fn (pipeline-fn pl (map (comp :step meta) steps) keep-result?)]
    (if (some #{pl} @pipelines)
      (throw (IllegalArgumentException.
               (str "A pipeline named " pl-name " already exists."))))
    (if (:durable opts)
      (msg/start pl :durable true)
      (msg/start pl))
    (binding [*pipeline* pl-fn]
      (let [listeners (->> steps
                           (map (partial pipeline-listen pl opts))
                           doall)]
        (swap! pipelines conj pl)
        (vary-meta pl-fn assoc :listeners listeners)))))

(defn ^{:valid-options
        #{:name :concurrency :error-handler :step-deref-timeout :fanout?}}
  step
  "Wraps the given function with the given options, returning a function.

   The following options are supported [default]:

   :name                a name to use for the step [the current index of the fn]
   :concurrency         the number of threads to use, overriding the pipeline
                        setting [1]
   :error-handler       an error handler function that can override the
                        pipeline setting [nil]
   :step-deref-timeout  the amount of time to wait when dereferencing
                        the result of the step if it returns a delay,
                        in ms. Overrides the pipeline setting [10 seconds]
   :fanout?             applies the fanout fn to the result of the step.
                        See fanout for more details [false]"
  [f & {:as opts}]
  (u/validate-options step opts)
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
