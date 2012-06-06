(ns tx.core
  (:use clojure.test)
  (:require [immutant.xa :as xa]
            [immutant.messaging :as msg]
            [clojure.java.jdbc :as sql]))

;;; Messages sent to 'in' will be reversed and sent to 'out'
(msg/start "/queue/in")
(msg/start "/queue/out")
(msg/listen "/queue/in" #(msg/publish "/queue/out" (apply str (reverse %))))

;;; In-memory, transactional database
(def ds (xa/datasource "foo" {:adapter "h2" :database "mem:foo"}))
(def spec {:datasource ds})

;;; 
(use-fixtures :each (fn [f] 
                      (sql/with-connection spec
                        (sql/create-table :things
                                          [:id :serial]
                                          [:name :varchar]))
                      (f)
                      (sql/with-connection spec
                        (sql/drop-table :things))))

(deftest insert-and-publish-should-commit
  (xa/transaction
   (sql/with-connection spec (sql/insert-records :things {:name "kiwi"}))
   (msg/publish "/queue/in" "kiwi"))
  (is (= "kiwi" (sql/with-connection spec (sql/with-query-results rows ["select name from things"]
                                            (:name (first rows))))))
  (is (= "iwik" (msg/receive "/queue/out"))))

(deftest insert-and-publish-should-rollback
  (try
    (xa/transaction
     (sql/with-connection spec (sql/insert-records :things {:name "kiwi"}))
     (msg/publish "/queue/in" "kiwi")
     (throw (Exception.)))
    (catch Exception _))
  (is (= 0 (sql/with-connection spec (sql/with-query-results rows ["select count(name) c from things"]
                                       (:c (first rows))))))
  (is (nil? (msg/receive "/queue/out" :timeout 3000))))
