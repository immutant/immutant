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
