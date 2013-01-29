(ns tx.test.korma
  (:use clojure.test
        tx.korma
        korma.core)
  (:require [immutant.xa :as xa]))

(use-fixtures :each (fn [f]
                      (delete posts)
                      (delete authors)
                      (f)))

(deftest insert-and-select
  (insert authors (values {:id 1, :username "jim"}))
  (let [results (select authors)]
    (is (= 1 (count results)))
    (is (= "jim" (-> results first :username)))))

(deftest rollback-a-duplicate-insert
  (try
    (xa/transaction
     (insert authors (values {:id 1, :username "jim"}))
     (insert authors (values {:id 2, :username "jim"})))
    (catch Exception e
      (is (re-find #"authors_unique_username" (.getMessage e)))))
  (is (empty? (select authors))))

(deftest commit-an-insert
  (xa/transaction
   (insert authors (values {:id 1, :username "jim"})))
  (is (= 1 (count (select authors)))))
