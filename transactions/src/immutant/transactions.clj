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

(ns immutant.transactions
  "Providing support for distributed (XA) transactions"
  (:import [org.projectodd.wunderboss WunderBoss]
           [org.projectodd.wunderboss.transactions Transaction]))

(def tx (WunderBoss/findOrCreateComponent Transaction))

(def
  ^{:doc "The JTA TransactionManager"
    :tag javax.transaction.TransactionManager}
  manager (.manager tx))

(defmacro transaction
  "The body constitutes a new transaction and all actions on XA
  components therein either succeed or fail, atomically. Any exception
  tossed within the body will cause the transaction to rollback.
  Otherwise, the transaction is committed and the value of the last
  expression in the body is returned. This is semantically equivalent
  to the JEE RequiresNew transaction scope."
  [& body]
  (let [f `(fn [] ~@body)]
    `(.requiresNew tx ~f)))

(defn set-rollback-only
  "Modify the current transaction such that the only possible outcome
  is a rollback; useful when rollback is desired but an exception is
  not"
  []
  (.setRollbackOnly manager))

;;; Macros analagous to the JEE Transaction attribute scopes

(defmacro required
  "JEE Required - execute within current transaction, if any, otherwise wrap body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.required tx ~f)))

(defmacro requires-new
  "JEE RequiresNew - suspend current transaction, if any, and wrap body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.requiresNew tx ~f)))

(defmacro not-supported
  "JEE NotSupported - suspend current transaction, if any, and run body without a transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.notSupported tx ~f)))

(defmacro supports
  "JEE Supports - run body regardless of current transaction state (unpredictable)"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.supports tx ~f)))

(defmacro mandatory
  "JEE Mandatory - throws an exception unless there's an active transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.mandatory tx ~f)))

(defmacro never
  "JEE Never - throws an exception if there's an active transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(.never tx ~f)))
