(ns tx.core
  (:use clojure.test
        [immutant.util :only [in-immutant?]])
  (:require [immutant.xa :as ixa]
            [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [clojure.java.jdbc :as sql]))

;;; Create a JMS queue
(when (in-immutant?)
  (imsg/start "/queue/test")
  (imsg/start "/queue/remote-test"))

;;; And an Infinispan cache
(def cache (ic/cache "test"))

;;; And some transactional databases
(defonce h2 (future (ixa/datasource "h2" {:adapter "h2" :database "mem:foo"})))
;;; rds-create-db-instance oracle -s 10 -c db.m1.small -e oracle-se -u myuser -p mypassword --db-name mydb
(defonce oracle (future (ixa/datasource "oracle" {:adapter "oracle"
                                                  :host "oracle.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                                  :username "myuser"
                                                  :password "mypassword"
                                                  :database "mydb"})))
;;; rds-create-db-instance mysql -s 10 -c db.m1.small -e mysql -u myuser -p mypassword --db-name mydb
(defonce mysql (future (ixa/datasource "mysql" {:adapter "mysql"
                                                :host "mysql.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                                :username "myuser"
                                                :password "mypassword"
                                                :database "mydb"})))
;;; configured locally
(defonce postgres (future (ixa/datasource "postgres" {:adapter "postgresql"
                                                      :username "myuser"
                                                      :password "mypassword"
                                                      :database "mydb"})))
;;; nfi since --db-name isn't supported for RDS sqlserver-se instances
(defonce mssql (future (ixa/datasource "mssql" {:adapter "mssql"
                                                :host "mssql.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                                :username "myuser"
                                                :password "mypassword"
                                                :database "mydb"})))

;;; Helper methods to verify database activity
(defn write-thing-to-db [spec name]
  (sql/with-connection spec
    (sql/insert-records :things {:name name})))
(defn read-thing-from-db [spec name]
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things where name = ?" name]
      (first rows))))
(defn count-things-in-db [spec]
  (sql/with-connection spec
    (sql/with-query-results rows ["select count(*) c from things"]
      (int ((first rows) :c)))))

(defn attempt-transaction [ds & [f]]
  (try
    (ixa/transaction
     (write-thing-to-db {:datasource ds} "kiwi")
     (imsg/publish "/queue/test" "kiwi")
     (imsg/publish "/queue/remote-test" "starfruit" :host "localhost" :port 5445)
     (ic/put cache :a 1)
     (if f (f)))
    (catch Exception e (println "JC: wtf?" (.getMessage e)))))

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
             (attempt-transaction ds# #(throw (Exception. "rollback")))
             (verify-transaction-failure ds#)))))

(defn db-fixture [db]
  (fn [f]
    (try
      (sql/with-connection {:datasource db}
        (try (sql/drop-table :things) (catch Exception _))
        (sql/create-table :things [:name "varchar(50)"]))
      (catch Exception _))
    (f)))

(defn cache-fixture [f]
  (ic/delete-all cache)
  (f))

(defn testes [& dbs]
  (binding [*ns* *ns*]
    (in-ns 'tx.core)
    (apply use-fixtures :each cache-fixture (map #(db-fixture @(var-get (resolve (symbol %)))) dbs))
    (doseq [db dbs]
      (define-tests db))))

