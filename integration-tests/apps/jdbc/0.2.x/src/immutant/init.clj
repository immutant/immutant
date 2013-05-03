(ns immutant.init
  (:require [immutant.xa :as xa]
            [immutant.web :as web]
            [clojure.java.jdbc :as sql]))

(def spec {:datasource (xa/datasource "foo" {:adapter "h2" :database "mem:foo"})})

(sql/with-connection spec
  (sql/create-table :things
                    [:name :varchar]))

(xa/transaction
 (sql/with-connection spec
   (sql/insert-records :things {:name "success"})))

(xa/transaction
 (sql/with-connection spec
   (sql/set-rollback-only)
   (sql/delete-rows :things [true])))

(try
  (xa/transaction
   (sql/with-connection spec
     (sql/delete-rows :things [true])
     (throw (NegativeArraySizeException. "test rollback by exception"))))
  (catch NegativeArraySizeException _))

(defn read-names []
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things"]
      (:name (first rows)))))

;;; A web interface
(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str (read-names))})
(web/start "/thing" handler)
