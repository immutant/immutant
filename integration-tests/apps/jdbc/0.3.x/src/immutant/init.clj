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

(ns immutant.init
  (:require [immutant.xa :as xa]
            [immutant.web :as web]
            [clojure.java.jdbc :as sql]))

(def spec {:datasource (xa/datasource "foo" {:adapter "h2" :database "mem:foo"})})

(sql/with-connection spec
  (sql/create-table :things
                    [:name :varchar]))

(xa/transaction
 (sql/insert! spec :things {:name "success"}))

(xa/transaction
 (sql/db-transaction [con spec]
  (sql/delete! con :things [true])
  (sql/db-set-rollback-only! con)))

(try
  (xa/transaction
   (sql/delete! spec :things [true])
   (throw (NegativeArraySizeException. "test rollback by exception")))
  (catch NegativeArraySizeException _))

(defn read-names []
  (-> (sql/query spec ["select name from things"])
      first
      :name))

;;; A web interface
(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str (read-names))})
(web/start "/thing" handler)
