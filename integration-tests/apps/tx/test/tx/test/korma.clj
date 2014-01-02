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

(ns tx.test.korma
  (:use tx.korma
        clojure.test
        korma.core)
  (:require [immutant.xa :as xa]))

(use-fixtures :each (fn [f]
                      (delete posts)
                      (delete authors)
                      (f)))

(use-fixtures :once (fn [f] (tx.korma/init) (f)))

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

