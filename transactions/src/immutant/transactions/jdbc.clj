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

(ns immutant.transactions.jdbc
  "Enables `clojure.java.jdbc` to behave within an XA transaction"
  (:import [java.sql PreparedStatement Connection])
  (:require [immutant.transactions :refer (set-rollback-only)]
            [clojure.java.jdbc :as jdbc]))

(defn ^:no-doc prepared-statement
 [con ^PreparedStatement stmt]
 (reify PreparedStatement
   ;; Make the wrapper the statement's back-reference
   (getConnection [_] con)

   ;; Delegate everything else
   (executeBatch [_] (.executeBatch stmt))
   (executeUpdate [_] (.executeUpdate stmt))
   (executeQuery [_] (.executeQuery stmt))
   (getUpdateCount [_] (.getUpdateCount stmt))
   (getParameterMetaData [_] (.getParameterMetaData stmt))
   (getGeneratedKeys [_] (.getGeneratedKeys stmt))
   (setFetchSize [_ s] (.setFetchSize stmt s))
   (setMaxRows [_ m] (.setMaxRows stmt m))
   (setNull [_ x y] (.setNull stmt x y))
   (setObject [_ x y] (.setObject stmt x y))
   (addBatch [_ b] (.addBatch stmt b))
   (addBatch [_] (.addBatch stmt))
   (close [_] (.close stmt))))

(defn ^:no-doc connection
 [^Connection con]
 (reify Connection
   ;; Eat these since they're illegal on an XA connection
   (setAutoCommit [& _])
   (commit [_])
   (rollback [_] (set-rollback-only))

   ;; Ensure statement's back-reference points to this
   (^PreparedStatement prepareStatement [this ^String a]
     (prepared-statement this (.prepareStatement con a)))
   (^PreparedStatement prepareStatement [this ^String a ^int b]
     (prepared-statement this (.prepareStatement con a b)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c]
     (prepared-statement this (.prepareStatement con a b c)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c ^int d]
     (prepared-statement this (.prepareStatement con a b c d)))

   ;; Delegate everything else
   (close [_] (.close con))
   (getAutoCommit [_] (.getAutoCommit con))
   (createStatement [_] (.createStatement con))
   (getMetaData [_] (.getMetaData con))
   (getTransactionIsolation [_] (.getTransactionIsolation con))
   (setTransactionIsolation [_ v] (.setTransactionIsolation con v))
   (isReadOnly [_] (.isReadOnly con))
   (setReadOnly [_ v] (.setReadOnly con v))))

(defn factory
  "May be passed via the :factory option to a `clojure.java.jdbc` spec
  that turns operations illegal during an XA
  transaction (commit/rollback/setAutoCommit) into no-ops so that JDBC
  resources manipulated via `clojure.java.jdbc` may participate in a
  distributed transaction"
  [spec]
  (connection (jdbc/get-connection spec)))
