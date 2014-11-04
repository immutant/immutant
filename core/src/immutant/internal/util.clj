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

(ns ^:no-doc ^:internal immutant.internal.util
    "Various internal utility functions."
    (:require [clojure.string :as str]
              [clojure.walk :refer (keywordize-keys)])
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

(def kwargs-or-map->map
  "If vals contains one value, return it. Otherwise, treat as kwargs and coerce to a map.

   In either case, pass it through keywordize-keys"
  (comp keywordize-keys kwargs-or-map->raw-map))

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

(defmacro backoff
  "A simple backoff strategy that retries body in the event of error.
   The first retry occurs after sleeping start milliseconds, the next
   after start*2 ms, and so on, until the sleep time exceeds end ms,
   at which point the caught error is tossed."
  [start end & body]
  `(loop [x# ~start]
     (let [result# (try
                    ~@body
                    (catch Exception e# (if (> x# ~end) (throw e#))))]
       (or result# (do (Thread/sleep x#) (recur (* 2 x#)))))))

(defn uuid []
  "Generates a random uuid string."
  (str (java.util.UUID/randomUUID)))

(defn logger
  [ns]
  (WunderBoss/logger (str ns)))

(defmacro warn
  "Logs as warn."
  [& msg]
  `(.warn (logger ~*ns*) (print-str ~@msg)))

(defmacro error
  "Logs as error."
  [& msg]
  `(.error (logger ~*ns*) (print-str ~@msg)))

(defmacro info
  "Logs as info."
  [& msg]
  `(.info (logger ~*ns*) (print-str ~@msg)))

(defmacro debug
  "Logs as debug."
  [& msg]
  `(.debug (logger ~*ns*) (print-str ~@msg)))
