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
  "Distributed XA transactional support"
  (:require [immutant.registry :as lookup]))

(def manager (lookup/fetch "jboss.txn.TransactionManager"))
  
(defn current
  "Return the active transaction"
  []
  (and manager (.getTransaction manager)))

(defn active?
  "True if currently running within a transaction"
  []
  (not (nil? (current))))

(defn enlist
  "Enlist resources in the current transaction"
  [& resources]
  (let [tx (current)]
    (doseq [resource resources] (.enlistResource tx resource))))

(defn suspend
  "Suspend, invoke, resume"
  [func]
  (let [tx (.suspend manager)]
    (try
      (func)
      (finally
       (.resume manager tx)))))

(defn begin
  "Begin, invoke, commit, rollback if error"
  [func]
  (.begin manager)
  (try
    (let [result (func)]
      (.commit manager)
      result)
    (catch Throwable e
      (.rollback manager)
      (throw e))))

(defmacro required
  "JEE Required"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (~f)
       (begin ~f))))

(defmacro requires-new
  "JEE RequiresNew"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend #(begin ~f))
       (begin ~f))))

(defmacro mandatory
  "JEE Mandatory"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (~f)
       (throw (Exception. "No active transaction")))))

(defmacro not-supported
  "JEE NotSupported"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (suspend ~f)
       (~f))))

(defmacro supports
  "JEE Supports (kinda silly)"
  [& body]
  (let [f `(fn [] ~@body)]
    `(~f)))

(defmacro never
  "JEE Never"
  [& body]
  (let [f `(fn [] ~@body)]
    `(if (active?)
       (throw (Exception. "Active transaction detected"))
       (~f))))
