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

(ns immutant.transactions.jdbc
  "Enables `clojure.java.jdbc` to behave within an XA transaction"
  (:import [java.sql Connection Statement CallableStatement
            PreparedStatement DatabaseMetaData])
  (:require [immutant.transactions :refer (set-rollback-only)]
            [clojure.java.jdbc :as jdbc]))

(defmacro ^:private override-delegate
  [type delegate-expr & body]
  (let [delegate (gensym)
        overrides (group-by first body)
        methods (for [m (.getMethods (resolve type))
                      :let [f (-> (.getName m) symbol (with-meta {:tag (-> m .getReturnType .getName)}))]
                      :when (not (overrides f))
                      :let [args (for [t (.getParameterTypes m)] (with-meta (gensym) {:tag (.getName t)}))]]
                  (list f (vec (conj args '_)) `(. ~delegate ~f ~@(map #(with-meta % nil) args))))]
    `(let [~delegate ~delegate-expr]
       (reify ~type ~@body ~@methods))))

(defmacro connection-backref
  "TODO: make this work"
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

   (^Statement createStatement [this]
     (override-delegate Statement (.createStatement con)
       (getConnection [_] this)))
   (^Statement createStatement [this ^int a ^int b]
     (override-delegate Statement (.createStatement con a b)
       (getConnection [_] this)))
   (^Statement createStatement [this ^int a ^int b ^int c]
     (override-delegate Statement (.createStatement con a b c)
       (getConnection [_] this)))

   (^CallableStatement prepareCall [this ^String a]
     (override-delegate CallableStatement (.prepareCall con a)
       (getConnection [_] this)))
   (^CallableStatement prepareCall [this ^String a ^int b ^int c]
     (override-delegate CallableStatement (.prepareCall con a b c)
       (getConnection [_] this)))
   (^CallableStatement prepareCall [this ^String a ^int b ^int c ^int d]
     (override-delegate CallableStatement (.prepareCall con a b c d)
       (getConnection [_] this)))

   (^PreparedStatement prepareStatement [this ^String a]
     (override-delegate PreparedStatement (.prepareStatement con a)
       (getConnection [_] this)))
   (^PreparedStatement prepareStatement [this ^String a ^int b]
     (override-delegate PreparedStatement (.prepareStatement con a b)
       (getConnection [_] this)))
   (^PreparedStatement prepareStatement [this ^String a ^ints b]
     (override-delegate PreparedStatement (.prepareStatement con a b)
       (getConnection [_] this)))
   (^PreparedStatement prepareStatement [this ^String a ^"[Ljava.lang.String;" b]
     (override-delegate PreparedStatement (.prepareStatement con a b)
       (getConnection [_] this)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c]
     (override-delegate PreparedStatement (.prepareStatement con a b c)
       (getConnection [_] this)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c ^int d]
     (override-delegate PreparedStatement (.prepareStatement con a b c d)
       (getConnection [_] this)))

   (^DatabaseMetaData getMetaData [this]
     (override-delegate DatabaseMetaData (.getMetaData con)
       (getConnection [_] this)))))

(defn factory
  "May be passed via the :factory option to a `clojure.java.jdbc` spec
  that turns operations illegal during an XA
  transaction (commit/rollback/setAutoCommit) into no-ops so that JDBC
  resources manipulated via `clojure.java.jdbc` may participate in a
  distributed transaction"
  [spec]
  (connection (jdbc/get-connection spec)))
