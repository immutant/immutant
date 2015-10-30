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
  (:import [java.sql PreparedStatement Connection])
  (:require [immutant.transactions :refer (set-rollback-only)]
            [clojure.java.jdbc :as jdbc]))

(defmacro ^:private override-delegate
  [type delegate & body]
  (let [overrides (group-by first body)
        methods (for [m (.getMethods (resolve type))
                      :let [f (with-meta (symbol (.getName m)) {:tag (-> m .getReturnType .getName)})]
                      :when (not (overrides f))
                      :let [args (for [t (.getParameterTypes m)] (with-meta (gensym) {:tag (.getName t)}))]]
                  (list f (vec (conj args '_)) `(. ~delegate ~f ~@(map #(with-meta % nil) args))))]
    `(reify ~type ~@body ~@methods)))

(defn ^:no-doc prepared-statement
 [con ^PreparedStatement stmt]
 (override-delegate PreparedStatement stmt
   ;; Make the wrapper the statement's back-reference
   (getConnection [_] con)))

(defn ^:no-doc connection
 [^Connection con]
 (override-delegate Connection con
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
     (prepared-statement this (.prepareStatement con a b c d)))))

(defn factory
  "May be passed via the :factory option to a `clojure.java.jdbc` spec
  that turns operations illegal during an XA
  transaction (commit/rollback/setAutoCommit) into no-ops so that JDBC
  resources manipulated via `clojure.java.jdbc` may participate in a
  distributed transaction"
  [spec]
  (connection (jdbc/get-connection spec)))
