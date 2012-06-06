(ns tx.core
  (:use clojure.test)
  (:require [immutant.xa :as xa]
            [immutant.cache :as c]
            [immutant.messaging :as msg]
            [clojure.java.jdbc :as sql]))

;;; Messages sent to 'in' will be reversed and sent to 'out'
(msg/start "/queue/in")
(msg/start "/queue/out")
(msg/listen "/queue/in" #(msg/publish "/queue/out" (apply str (reverse %))))

;;; Infinispan cache
(def cache (c/cache "stuff"))

;;; In-memory, transactional database
(defonce ds (xa/datasource "foo" {:adapter "h2" :database "mem:foo"}))
(def spec {:datasource ds})

;;; Ensure each test starts with an empty table
(use-fixtures :each (fn [f]
                      (c/delete-all cache)
                      (sql/with-connection spec
                        (try (sql/drop-table :things) (catch Exception _))
                        (sql/create-table :things [:id :serial] [:name :varchar]))
                      (f)))

;;; Helper methods to verify database activity
(defn write-thing [name]
  (sql/with-connection spec
    (sql/insert-records :things {:name name})))
(defn read-thing [name]
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things where name = ?" name]
      (first rows))))
(defn count-things []
  (sql/with-connection spec
    (sql/with-query-results rows ["select count(*) c from things"]
      ((first rows) :c))))


;;; Test definitions

(deftest db+msg+cache-should-commit
  (xa/transaction
   (write-thing "kiwi")
   (msg/publish "/queue/in" "kiwi")
   (c/put cache :a 1))
  (is (= "kiwi" (:name (read-thing "kiwi"))))
  (is (= 1 (count-things)))
  (is (= 1 (:a cache)))
  (is (= "iwik" (msg/receive "/queue/out"))))

(deftest db+msg+cache-should-rollback
  (try
    (xa/transaction
     (write-thing "kiwi")
     (msg/publish "/queue/in" "kiwi")
     (c/put cache :a 1)
     (throw (Exception.)))
    (catch Exception _))
  (is (nil? (read-thing "kiwi")))
  (is (= 0 (count-things)))
  (is (nil? (:a cache)))
  (is (nil? (msg/receive "/queue/out" :timeout 2000))))
