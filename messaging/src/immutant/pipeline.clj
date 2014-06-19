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

(ns immutant.pipeline
  "Provides functions for creating and managing pipelines. A pipeline
   is a composition of functions (\"steps\"), where each function is
   passed the result of the previous function, dereferenced if needed.
   It is built on top of Immutant's messaging subsystem, allowing each
   step to have multiple processing threads, and to be automatically
   load balanced across a cluster.

   The {{pipeline}} function takes a unique (within the scope of the
   application) name, one or more single-arity functions, and optional
   kwarg options, returning a function that places its argument onto
   the pipeline when called. The resulting pipeline-fn optionally
   returns a delay that can be used to retrieve the result of the
   pipeline execution.

   Each function can be optionally be wrapped with metadata that
   provides options for how that particular function is handled (see
   the 'step' fn below).

   Example:

   ```
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
   (pl/stop foo-pipeline)
   ```"
  (:require [immutant.logging          :as log]
            [immutant.messaging        :as msg]
            [immutant.messaging.codecs :as codecs]
            [immutant.internal.options :as o]
            ;;[immutant.xa.transaction :as tx]
            [immutant.internal.util    :as u]
            [immutant.util             :as pu])
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

(def ^:dynamic ^:private *steps*
  "A map of the currently active steps."
  nil)

(def halt
  "Halts pipeline processing for a given message if returned from any
   handler function."
  ::halt)

(def ^:private correlation-property "correlationID")

(defn- get-correlation-property [m]
  (get (.properties m) correlation-property))

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

(defonce ^{:private true
           :doc "Stores the currently active pipelines"}
  pipelines
  (atom {}))

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

(defn- wrap-decode [f]
  #(f
     (if (-> *steps* (get *current-step*) meta (:decode? true))
       (codecs/decode %)
       %)))

(defn- wrap-result-passing
  [f pl current-step next-step opts]
  (if next-step
    (fn [raw-message]
      (let [timeout (:step-deref-timeout opts 60000) ;; 10s
            m (u/maybe-deref (f raw-message) timeout ::timeout)]
        (condp = m
          halt      nil
          ::timeout (throw (TimeoutException.
                             (format "Timed out after %d ms when dereferencing the result of step %s"
                               timeout current-step)))
          (msg/publish pl m
            :encoding :edn
            :properties {"step" next-step
                         correlation-property (get-correlation-property raw-message)}))))
    f))

#_(defn- wrap-no-tx
    [f]
    (fn [m]
      (tx/not-supported (f m))))

(defn- pipeline-listen
  "Creates a listener on the pipeline for the given function."
  [pl opts f]
  (let [{:keys [step next-step]} (meta f)
        opts (-> opts
               (merge (meta f))
               (assoc :decode? false
                      :selector (str "step = '" step "'")))]
    (msg/listen pl
      (-> f
        (wrap-fanout opts)
        wrap-decode
        (wrap-result-passing pl step next-step opts)
        (wrap-error-handler opts)
        (wrap-step-bindings step next-step)
        #_wrap-no-tx)
      opts)))

(defn ^:internal ^:no-doc delayed
  "Creates an timeout-derefable delay around any function taking a timeout and timeout-val."
  [f]
  (let [val (atom ::unrealized)
        realized? #(not= ::unrealized @val)
        rcv (fn [timeout timeout-val]
              (reset! val (f timeout timeout-val)))]
    (proxy [clojure.lang.Delay clojure.lang.IBlockingDeref] [nil]
      (deref
        ([]
           (if (realized?) @val (rcv 0 nil)))
        ([timeout-ms timeout-val]
           (if (realized?)
             @val
             (rcv timeout-ms timeout-val))))
      (isRealized []
        (realized?)))))

(defn- create-delay
  [pl id keep-result?]
  (if keep-result?
    (delayed
      (fn [t t-val]
        (msg/receive pl
          :timeout t
          :timeout-val t-val
          :selector (format "%s='%s' AND result = true" correlation-property id))))
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
          :encoding :edn
          :properties {"step" step, correlation-property id})
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
  (fn [m]
    (msg/publish pl (if m (codecs/decode m))
      :encoding :edn
      :ttl ttl
      :properties {"result" true, correlation-property (get-correlation-property m)})))

(defn ^{:valid-options
        #{:concurrency :error-handler :result-ttl :step-deref-timeout :durable}}
  pipeline
  "Creates a pipeline function.

   It takes a unique (within the scope of the application) name, one
   or more single-arity functions, and optional kwarg options, and
   returns a function that places its argument onto the pipeline when
   called. If :result-ttl is > -1, it returns a delayed value that can
   be derefed to get the result of the pipeline execution.

   The following options are supported, and must follow the step
   functions as either kwarg arguments or a map [default]:

   * :concurrency        the number of threads to use for *each* step. Can be
                         overridden on a per-step basis - see the 'step'
                         function. [1]
   * :error-handler      a function that will be called when any step raises
                         an exception. It will be passed the exception and
                         the argument to the step. Without an error-handler,
                         the default HornetQ retry semantics will be
                         used. Can be overridden on a per-step basis - see
                         the 'step' function. [nil]
   * :result-ttl         the time-to-live for the final pipeline result,
                         in millis. Set to 0 for \"forever\", -1 to disable
                         returning the result via a delay [1 hour]
   * :step-deref-timeout the amount of time to wait when dereferencing
                         the result of a step that returns a delay,
                         in millis. Can be overridden on a per-step basis -
                         see the 'step' function. [10 seconds]
   * :durable            whether messages persist across restarts [true]

   During the execution of each step and each error-handler call, the
   following vars are bound:

   * `*pipeline*`      the pipeline (as a fn) that is being executed
   * `*current-step*`  the name of the currently executing step
   * `*next-step*`     the name of the next step in the pipeline

   This function is *not* idempotent. Attempting to create a pipeline
   with the same name as an existing pipeline will raise an error."
  [pl-name & args]
  (let [opts (o/validate-options (u/kwargs-or-map->map (drop-while fn? args)) pipeline)
        pl-name (name pl-name)
        pl (msg/queue (format "%s.pipeline-%s"
                        (pu/app-name) pl-name)
             :durable (:durable opts true))
        result-ttl (:result-ttl opts 3600000) ;; 1 hr
        keep-result? (>= result-ttl 0)
        steps (-> (take-while fn? args)
                vec
                (#(if keep-result?
                    (conj % (vary-meta (sync-result-step-fn pl result-ttl) assoc
                              :decode? false))
                    %))
                named-steps)
        pl-fn (pipeline-fn pl (map (comp :step meta) steps) keep-result?)]
    (when (get @pipelines pl-name)
      (throw (IllegalArgumentException.
               (str "A pipeline named " pl-name " already exists."))))
    (binding [*pipeline* pl-fn
              *steps* (zipmap (map #(-> % meta :step) steps) steps)]
      (let [listeners (mapv (partial pipeline-listen pl opts) steps)
            final (vary-meta pl-fn assoc
                    :listeners listeners
                    :name pl-name)]
        (swap! pipelines assoc pl-name final)
        final))))

(defn ^{:valid-options
        #{:name :concurrency :error-handler :step-deref-timeout :fanout?}}
  step
  "Wraps the given function with the given options, returning a function.

   The following options can be passed as kwargs or as a map [default]:

   * :name                a name to use for the step [the current index of the fn]
   * :concurrency         the number of threads to use, overriding the pipeline
                          setting [1]
   * :error-handler       an error handler function that can override the
                          pipeline setting [nil]
   * :step-deref-timeout  the amount of time to wait when dereferencing
                          the result of the step if it returns a delay,
                          in ms. Overrides the pipeline setting [10 seconds]
   * :fanout?             applies the fanout fn to the result of the step.
                          See {{fanout}} for more details [false]"
  [f & opts]
  (let [opts (u/kwargs-or-map->map opts)]
    (o/validate-options  opts step)
    (vary-meta f merge opts)))

(defn stop
  "Destroys a pipeline.

   `pl` can either be the pipeline fn returned by {{pipeline}}, or the
   name of the pipeline.

   Typically not necessary since it will be done for you when your app
   is undeployed."
  [pl]
  (let [pl (get @pipelines (if (keyword? pl) (name pl) pl) pl)
        {:keys [listeners pipeline name]} (meta pl)]
    (swap! pipelines dissoc name)
    (doseq [l listeners]
      (msg/stop l))
    (msg/stop pipeline)))
