;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc ^:internal immutant.internal.util
    "Various internal utility functions."
    (:require [clojure.string :as str])
    (:import clojure.lang.IDeref
             java.util.UUID
             org.projectodd.wunderboss.WunderBoss))

(defn hash-based-component-name [defaults opts]
  (->> opts
    (merge defaults)
    hash
    str))

(defn kwargs-or-map->raw-map
  "If vals contains one value, return it. Otherwise, treat as kwargs and coerce to a map."
  [vals]
  (if (= 1 (count vals))
    (let [m (first vals)]
      (if (map? m) m (apply hash-map m)))
    (apply hash-map vals)))

(defn scrub-keys
  "Strip any non-alpha prefix off all keys that are strings"
  [m]
  (reduce
    (fn [m [k v]] (assoc m (if (string? k) (.replaceFirst ^String k "^[^a-z]+" "") k) v))
    {}
    m))

(defn kwargs-or-map->map
  "If vals contains one value, return it. Otherwise, treat as kwargs and coerce to a map.

   In either case, turn the keys into keywords"
  [v]
  (->> v
    kwargs-or-map->raw-map
    scrub-keys
    (map (fn [[k v]] (if (string? k) [(keyword k) v] [k v])))
    (into {})))

(defn require-resolve
  "Requires and resolves the given namespace-qualified symbol."
  [sym]
  (require (symbol (namespace sym)))
  (resolve sym))

(defn try-resolve
  "Tries to require and resolve the given namespace-qualified symbol, returning nil if not found."
  [sym]
  (try
    (require-resolve sym)
    (catch java.io.FileNotFoundException _)
    (catch RuntimeException _)))

(defn try-resolve-any
  "Tries to resolve the given namespace-qualified symbols. Returns the
   first successfully resolved symbol, or nil if none of the given symbols
   resolve."
  [& syms]
  (if-let [sym (try-resolve (first syms))]
    sym
    (if-let [tail (seq (rest syms))]
      (apply try-resolve-any tail)
      (throw (IllegalArgumentException.
               "Unable to resolve a valid symbol from the given list.")))))

(defn try-resolve-throw
  "Tries to resolve `sym` via [[try-resolve]], throwing with `message` on failure."
  [sym message]
  (if-let [v (try-resolve sym)]
    v
    (throw (IllegalStateException. (format "Can't resolve %s, %s" sym message)))))

(defn try-import
  "Tries to import the given symbol, returning the class on success."
  [sym]
  (try
    (eval `(import (quote ~sym)))
    (catch Throwable _)))

(defmacro when-import
  "Executes body when sym is successfully imported"
  [sym & body]
  `(when (try-import ~sym)
     ~@body))

(defn maybe-deref
  "derefs v if it is derefable, otherwise returns v"
  [v & args]
  (if (instance? IDeref v)
    (apply deref v args)
    v))

(defmacro with-tccl
  "Evaluates `body` with the tccl set to `cl`, restoring the original
   tccl when done."
  [cl & body]
  `(let [thread# (Thread/currentThread)
         curr-cl# (.getContextClassLoader thread#)]
     (.setContextClassLoader thread# ~cl)
     (try
       ~@body
       (finally
         (.setContextClassLoader thread# curr-cl#)))))

(defn uuid
  "Generates a random uuid string."
  []
  (str (java.util.UUID/randomUUID)))

(defn ^org.slf4j.Logger logger
  [ns]
  (WunderBoss/logger (str ns)))

(defn ^:internal handle-log-args [& args]
  (update-in
    (if (instance? Throwable (last args))
      [(butlast args) (last args)]
      [args])
    [0] #(apply print-str %)))

(defmacro warn
  "Logs as warn."
  [& msg]
  `(let [[^String m# ^Throwable t#] (handle-log-args ~@msg)]
    (.warn (logger ~*ns*) m# t#)))

(defmacro error
  "Logs as error."
  [& msg]
  `(let [[^String m# ^Throwable t#] (handle-log-args ~@msg)]
     (.error (logger ~*ns*) m# t#)))

(defmacro info
  "Logs as info."
  [& msg]
  `(let [[^String m# ^Throwable t#] (handle-log-args ~@msg)]
     (.info (logger ~*ns*) m# t#)))

(defmacro debug
  "Logs as debug."
  [& msg]
  `(let [[^String m# ^Throwable t#] (handle-log-args ~@msg)]
     (.debug (logger ~*ns*) m# t#)))

(def ^:dynamic *warn-on-deprecation* true)

(def warn-deprecated*
  (memoize
    (fn [old new]
      (warn (format "%s is deprecated, use %s instead" old new)))))

(defn warn-deprecated
  [old new]
  (when *warn-on-deprecation*
    (warn-deprecated* old new)))
