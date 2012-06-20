(ns tx.core
  (:use clojure.test)
  (:require [immutant.xa :as ixa]
            [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [clojure.java.jdbc :as sql]))

;;; Create a JMS queue
(imsg/start "/queue/test")

;;; And an Infinispan cache
(def cache (ic/cache "test"))

;;; And some transactional databases
(defonce h2 (ixa/datasource "h2" {:adapter "h2" :database "mem:foo"}))
(defonce oracle (ixa/datasource "oracle" {:adapter "oracle"
                                          :host "oracle.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                          :username "myuser"
                                          :password "mypassword"
                                          :database "mydb"}))
(defonce mysql (ixa/datasource "mysql" {:adapter "mysql"
                                        :host "mysql.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                        :username "myuser"
                                        :password "mypassword"
                                        :database "mydb"}))

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

(defn attempt-transaction [db & [f]]
  (try
    (ixa/transaction
     (write-thing-to-db {:datasource db} "kiwi")
     (imsg/publish "/queue/test" "kiwi")
     (ic/put cache :a 1)
     (if f (f)))
    (catch Exception _)))

(defn verify-transaction-success [db]
  (is (= "kiwi" (:name (read-thing-from-db {:datasource db} "kiwi"))))
  (is (= 1 (count-things-in-db {:datasource db})))
  (is (= "kiwi" (imsg/receive "/queue/test")))
  (is (= 1 (:a cache))))

(defn verify-transaction-failure [db]
  (is (nil? (read-thing-from-db {:datasource db} "kiwi")))
  (is (= 0 (count-things-in-db {:datasource db})))
  (is (nil? (:a cache)))
  (is (nil? (imsg/receive "/queue/test" :timeout 2000))))

(defn define-h2-tests []
  (deftest h2-db+msg+cache-should-commit
    (attempt-transaction h2)
    (verify-transaction-success h2))
  (deftest h2-db+msg+cache-should-rollback
    (attempt-transaction h2 #(throw (Exception. "rollback")))
    (verify-transaction-failure h2)))

(defn define-oracle-tests []
  (deftest oracle-db+msg+cache-should-commit
    (attempt-transaction oracle)
    (verify-transaction-success oracle))
  (deftest oracle-db+msg+cache-should-rollback
    (attempt-transaction oracle #(throw (Exception. "rollback")))
    (verify-transaction-failure oracle)))

(defn define-mysql-tests []
  (deftest mysql-db+msg+cache-should-commit
    (attempt-transaction mysql)
    (verify-transaction-success mysql))
  (deftest mysql-db+msg+cache-should-rollback
    (attempt-transaction mysql #(throw (Exception. "rollback")))
    (verify-transaction-failure mysql)))

(defmacro define-tests [db]
  `(let [ds# (var-get (resolve (symbol ~db)))]
     (list
      (deftest ~(gensym "commit-") 
        (attempt-transaction ds#)
        (verify-transaction-success ds#))
      (deftest ~(gensym "rollback-")
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
    (try
      (apply use-fixtures :each cache-fixture (map #(db-fixture (var-get (resolve (symbol %)))) dbs))
      (doseq [db dbs]
        (define-tests db))
      (catch Exception _))
    (run-tests)))