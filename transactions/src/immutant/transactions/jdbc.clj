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

(ns immutant.transactions.jdbc
  "Enables `clojure.java.jdbc` to behave within an XA transaction"
  (:import [java.sql Connection Statement CallableStatement
            PreparedStatement DatabaseMetaData]
           java.lang.reflect.Method)
  (:require [immutant.transactions :refer (set-rollback-only)]
            [clojure.java.jdbc :as jdbc]))

(defmacro ^:private override-delegate
  [type delegate-expr & body]
  (let [delegate (gensym)
        expanded (map macroexpand body)
        overrides (group-by first expanded)
        methods (for [^Method m (.getMethods ^Class (resolve type))
                      :let [f (-> (.getName m) symbol (with-meta {:tag (-> m .getReturnType .getName)}))]
                      :when (not (overrides f))
                      :let [args (for [^Class t (.getParameterTypes m)] (with-meta (gensym) {:tag (.getName t)}))]]
                  (list f (vec (conj args '_)) `(. ~delegate ~f ~@(map #(with-meta % nil) args))))]
    `(let [~delegate ~delegate-expr]
       (reify ~type ~@expanded ~@methods))))

(defmacro ^:private with-backref
  "Expands to a reify spec for a Connection with an override-delegate
  call for a Statement, its specific type taken from (:tag (meta f)),
  created by invoking f on con, that overrides getConnection to return
  this, the reified Connection"
  [con f args]
  (list f (vec (cons 'this args))
    (list 'override-delegate (:tag (meta f)) `(. ~con ~f ~@(map #(with-meta % nil) args))
       '(getConnection [_] this))))

(defn ^:no-doc connection
 [^Connection con]
 (override-delegate Connection con

   ;; Eat these since they're illegal on an XA connection

   (setAutoCommit [& _])
   (commit [_])
   (rollback [_] (set-rollback-only))

   ;; Ensure each statement's connection back-reference points to this

   (with-backref con ^Statement createStatement [])
   (with-backref con ^Statement createStatement [^int a ^int b])
   (with-backref con ^Statement createStatement [^int a ^int b ^int c])

   (with-backref con ^CallableStatement prepareCall [^String a])
   (with-backref con ^CallableStatement prepareCall [^String a ^int b ^int c])
   (with-backref con ^CallableStatement prepareCall [^String a ^int b ^int c ^int d])

   (with-backref con ^PreparedStatement prepareStatement [^String a])
   (with-backref con ^PreparedStatement prepareStatement [^String a ^int b])
   (with-backref con ^PreparedStatement prepareStatement [^String a ^ints b])
   (with-backref con ^PreparedStatement prepareStatement [^String a ^"[Ljava.lang.String;" b])
   (with-backref con ^PreparedStatement prepareStatement [^String a ^int b ^int c])
   (with-backref con ^PreparedStatement prepareStatement [^String a ^int b ^int c ^int d])

   (with-backref con ^DatabaseMetaData getMetaData [])))

(defn factory
  "May be passed via the :factory option to a `clojure.java.jdbc` spec
  that turns operations illegal during an XA
  transaction (commit/rollback/setAutoCommit) into no-ops so that JDBC
  resources manipulated via `clojure.java.jdbc` may participate in a
  distributed transaction"
  [spec]
  (connection (jdbc/get-connection spec)))
