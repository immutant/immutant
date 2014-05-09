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
  (:require [immutant.util :as u])
  (:import clojure.lang.IDeref
           java.util.UUID))

(defn hash-based-component-name [defaults opts]
  (->> opts
    (merge defaults)
    .hashCode
    str))

(defn kwargs-or-map->map
  "If vals contains one value, return it. Otherwise, treat as kwargs and coerce to a map."
  [vals]
  (if (= 1 (count vals))
    (first vals)
    (apply hash-map vals)))

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

(defn wait-for
  "Waits for (t) to be true before invoking f, if passed. Evaluates
   test every 100 ms attempts times before giving up. Attempts
   defaults to 300. Passing :forever for attempts will loop until the
   end of time or (t) is true, whichever comes first."
  ([t]
     (wait-for t (constantly true)))
  ([t f]
     (wait-for t f 300))
  ([t f attempts]
     (let [wait #(Thread/sleep 100)]
       (cond
        (t)                   (f)
        (= :forever attempts) (do
                                (wait)
                                (recur t f :forever))
        (< attempts 0)        (throw (IllegalStateException.
                                      (str "Gave up waiting for " t)))
        :default              (do
                                (wait)
                                (recur t f (dec attempts)))))))

(defn wait-for-start
  "Waits for (.isStarted x) to be true before returning or invoking f."
  ([x]
     (wait-for-start x (constantly x)))
  ([x f]
     (wait-for-start x f 300))
  ([x f attempts]
     (wait-for #(.isStarted x) f attempts)))

(defn waiting-derefable
  "Returns an IDeref/IBlockingDeref that completes the deref and returns x when
   (t) is true."
  [t x]
  (reify
    clojure.lang.IDeref
    (deref [_]
      (wait-for t (constantly x) :forever))
    clojure.lang.IBlockingDeref
    (deref [_ ms timeout-val]
      (try
        (wait-for t (constantly x) (int (/ ms 100)))
        (catch IllegalStateException _
          timeout-val)))))

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
