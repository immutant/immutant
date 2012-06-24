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

(ns immutant.xa.transaction
  "Fine-grained XA transactional control"
  (:use [clojure.java.jdbc :only [transaction*]])
  (:require [immutant.registry :as lookup]))

(def ^javax.transaction.TransactionManager
  manager (lookup/fetch "jboss.txn.TransactionManager"))

(defn available?
  "Returns true if a TransactionManager is available to manage XA transactions"
  []
  (not (nil? manager)))

(defn current
  "Return the active transaction"
  []
  (and manager (.getTransaction manager)))

(defn active?
  "Returns true if currently running within a transaction"
  []
  (not (nil? (current))))

(defn enlist
  "Enlist XA resources in the current transaction"
  [& resources]
  (let [tx (current)]
    (doseq [resource resources] (.enlistResource tx resource))))

(defn after-completion
  "Register a callback to fire when the current transaction is complete"
  [f]
  (.registerSynchronization (current)
                            (reify javax.transaction.Synchronization
                              (beforeCompletion [_])
                              (afterCompletion [_ _] (f)))))

;;; Monkey-patchery to prevent calls to setAutoCommit/commit/rollback on connection
(in-ns 'clojure.java.jdbc)
(def ^{:dynamic true} xa-transaction* transaction*)
(defn transaction* [& args] (apply xa-transaction* args))
(in-ns 'immutant.xa.transaction)


;;; The functions that enable the various transactional scope macros

(defn begin
  "Begin, invoke func, commit, rollback if error"
  [func]
  (binding [clojure.java.jdbc/xa-transaction* (fn [f] (f))]
    (.begin manager)
    (try
      (let [result (func)]
        (.commit manager)
        result)
      (catch Throwable e
        (.rollback manager)
        (throw e)))))

(defn suspend
  "Suspend, invoke func, resume"
  [func]
  (let [tx (.suspend manager)]
    (try
      (func)
      (finally
       (.resume manager tx)))))


;;; Macros analagous to the JEE Transaction attribute scopes

(defmacro required
  "JEE Required - execute within current transaction, if any, otherwise start one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (~f)
       (begin ~f))))

(defmacro requires-new
  "JEE RequiresNew - suspend current transaction, if any, and start a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend #(begin ~f))
       (begin ~f))))

(defmacro not-supported
  "JEE NotSupported - suspend current transaction, if any, and run body outside of any transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend ~f)
       (~f))))

(defmacro supports
  "JEE Supports - run body inside current transaction, if any, otherwise outside (unpredictable)"
  [& body]
  (let [f `(fn [] ~@body)]
    `(~f)))

(defmacro mandatory
  "JEE Mandatory - throws an exception unless there's an active transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (~f)
       (throw (Exception. "No active transaction")))))

(defmacro never
  "JEE Never - throws an exception if there's an active transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (throw (Exception. "Active transaction detected"))
       (~f))))
