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

(def ^:no-doc ^Transaction tx (WunderBoss/findOrCreateComponent Transaction))

(def
  ^{:doc "The JTA TransactionManager"
    :tag javax.transaction.TransactionManager}
  manager (.manager tx))

(defmacro transaction
  "Execute body within current transaction, if any, otherwise start a
  new transaction, execute body, and either commit or rollback all
  actions on XA components contained therein atomically. Any exception
  tossed within the body will cause the transaction to rollback.
  Otherwise, the transaction is committed and the value of the last
  expression in the body is returned. This is effectively an alias for
  the [[immutant.transactions.scope/required]] transaction scope."
  [& body]
  (let [f `(fn [] ~@body)]
    `(.required tx ~f)))

(defn set-rollback-only
  "Modify the current transaction such that the only possible outcome
  is a rollback; useful when rollback is desired but an exception is
  not"
  []
  (.setRollbackOnly manager))

(defn enlist
  "Enlist a valid XAResource as a participant in the current
  transaction. Not required for Immutant resources, i.e. messaging and
  caching, as they will be enlisted automatically."
  [^javax.transaction.xa.XAResource resource]
  (-> manager
    .getTransaction
    (.enlistResource resource)))
