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

(ns immutant.xa.transaction
  "Fine-grained XA transactional control"
  (:require [immutant.registry :as registry]
            [immutant.util     :as util]
            [immutant.logging  :as log]))

(def ^javax.transaction.TransactionManager manager
  (memoize (fn [] (registry/get "jboss.txn.TransactionManager"))))

(defn available?
  "Returns true if a TransactionManager is available to manage XA transactions"
  []
  (not (nil? (manager))))

(defn ^javax.transaction.Transaction current
  "Return the active transaction"
  []
  (if-let [mgr (manager)] (.getTransaction mgr)))

(defn active?
  "Returns true if currently running within a transaction"
  []
  (not (nil? (current))))

(defn set-rollback-only
  "Modify the current transaction such that the only possible outcome is a roll back"
  []
  (if-let [mgr (manager)] (.setRollbackOnly mgr)))

(defn enlist
  "Enlist XA resources in the current transaction"
  [& resources]
  (let [tx (current)]
    (doseq [resource resources] (.enlistResource tx resource))))

(defn after-completion
 "Register a callback to fire when the current transaction is complete"
 [f]
 (if (available?)
  (.registerSynchronization
   (current)
   (reify
     javax.transaction.Synchronization
     (afterCompletion [_ _]
       (f))
     (beforeCompletion [_])))
  (log/warn "transaction/after-completion called when no tx manager was available, ignoring.")))


;;; The functions that enable the various transactional scope macros

(defn begin
  "Begin, invoke func, commit, rollback if error"
  [func]
  (let [mgr (manager)]
    (.begin mgr)
    (try
      (let [result (func)]
        (.commit mgr)
        result)
      (catch javax.transaction.RollbackException ignored)
      (catch Throwable e
        (.rollback mgr)
        (throw e)))))

(defn suspend
  "Suspend, invoke func, resume"
  [func]
  (let [mgr (manager)
        tx (.suspend mgr)]
    (try
      (func)
      (finally
       (.resume mgr tx)))))


;;; Macros analagous to the JEE Transaction attribute scopes

(defmacro required
  "JEE Required - execute within current transaction, if any, otherwise wrap body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (~f)
       (begin ~f))))

(defmacro requires-new
  "JEE RequiresNew - suspend current transaction, if any, and wrap body in a new one"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend #(begin ~f))
       (begin ~f))))

(defmacro not-supported
  "JEE NotSupported - suspend current transaction, if any, and run body without a transaction"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend ~f)
       (~f))))

(defmacro supports
  "JEE Supports - run body regardless of current transaction state (unpredictable)"
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
