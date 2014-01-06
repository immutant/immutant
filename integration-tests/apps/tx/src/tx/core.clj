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

(ns tx.core
  (:use [immutant.util :only [in-immutant? hornetq-remoting-port]])
  (:require [immutant.xa :as ixa]
            [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [clojure.java.jdbc :as sql]))

;;; Create a JMS queue
(when (in-immutant?)
  (imsg/start "/queue/test")
  (imsg/start "/queue/remote-test"))

;;; And an Infinispan cache
(def cache (ic/create "tx.core"))

(defn cache-fixture [f]
  (ic/delete-all cache)
  (f))

;;; And some transactional databases
(defonce h2 (future (ixa/datasource "h2" {:adapter "h2" :database "mem:foo"})))
;;; rds-create-db-instance oracle -s 10 -c db.m1.small -e oracle-se -u myuser -p mypassword --db-name mydb
(defonce oracle (future (ixa/datasource "oracle" {:adapter "oracle"
                                                  :url "jdbc:oracle:thin:@//oracle.cpct4icp7nye.us-east-1.rds.amazonaws.com:1521/mydb"
                                                  :username "myuser"
                                                  :password "mypassword"})))
;;; rds-create-db-instance mysql -s 10 -c db.m1.small -e mysql -u myuser -p mypassword --db-name mydb
(defonce mysql (future (ixa/datasource "mysql" {:adapter "mysql"
                                                :url "jdbc:mysql://mysql.cpct4icp7nye.us-east-1.rds.amazonaws.com/mydb?user=myuser&password=mypassword"})))
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
     (imsg/publish "/queue/remote-test" "starfruit"
                   :host "localhost"
                   :port (if (in-immutant?)
                           (hornetq-remoting-port)
                           5445))
     (ic/put cache :a 1)
     (if f (f)))
    (catch Exception e
      (println "Caught exception:" (.getMessage e)))))

