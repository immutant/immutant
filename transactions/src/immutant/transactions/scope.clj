;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.transactions.scope
  "Macros that control transaction scope, analagous to
  [JEE transaction attributes](http://docs.oracle.com/javaee/7/tutorial/doc/transactions003.htm)"
  (:require [immutant.transactions :refer (tx)]))

(defmacro required
  "JEE Required - execute within current transaction, if any,
  otherwise run body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.required tx ~f)))

(defmacro requires-new
  "JEE RequiresNew - suspend current transaction, if any, and execute
  body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.requiresNew tx ~f)))

(defmacro not-supported
  "JEE NotSupported - suspend current transaction, if any, and run
  body without a transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.notSupported tx ~f)))

(defmacro supports
  "JEE Supports - run body regardless of current transaction
  state (unpredictable)"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.supports tx ~f)))

(defmacro mandatory
  "JEE Mandatory - throws an exception unless there's an active
  transaction in which body will be run"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.mandatory tx ~f)))

(defmacro never
  "JEE Never - throws an exception if there's an active transaction,
  otherwise runs body"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.never tx ~f)))
