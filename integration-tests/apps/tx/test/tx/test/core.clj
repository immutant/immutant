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

(ns tx.test.core
  (:use tx.core
        clojure.test)
  (:require [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [clojure.java.jdbc :as sql]
            [clojure.string :as str]))

(defn verify-transaction-success [ds]
  (is (= "kiwi" (imsg/receive "/queue/test" :timeout 2000)))
  (is (= "starfruit" (imsg/receive "/queue/remote-test")))
  (is (= "kiwi" (:name (read-thing-from-db {:datasource ds} "kiwi"))))
  (is (= 1 (count-things-in-db {:datasource ds})))
  (is (= 1 (:a cache))))

(defn verify-transaction-failure [ds]
  (is (nil? (imsg/receive "/queue/test" :timeout 2000)))
  (is (nil? (imsg/receive "/queue/remote-test" :timeout 2000)))
  (is (nil? (read-thing-from-db {:datasource ds} "kiwi")))
  (is (= 0 (count-things-in-db {:datasource ds})))
  (is (nil? (:a cache))))

(defn define-tests [db]
  (eval `(let [ds# @(var-get (resolve (symbol ~db)))]
           (deftest ~(symbol (str "commit-" db)) 
             (attempt-transaction ds#)
             (verify-transaction-success ds#))
           (deftest ~(symbol (str "rollback-" db))
             (attempt-transaction ds# #(throw (Exception. "force rollback")))
             (verify-transaction-failure ds#)))))

(defn db-fixture [db]
  (fn [f]
    (try
      (sql/with-connection {:datasource db}
        (try (sql/drop-table :things) (catch Exception _))
        (sql/create-table :things [:name "varchar(50)"]))
      (catch Exception _))
    (f)))

(defn testes [& dbs]
  (apply use-fixtures :each cache-fixture (map #(db-fixture @(var-get (resolve (symbol %)))) dbs))
  (doseq [db dbs]
    (define-tests db)))

(apply testes (str/split (:databases (immutant.registry/get :config) "h2") #","))
