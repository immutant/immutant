(ns immutant.init
  (:require [immutant.xa :as xa]
            [immutant.web :as web]
            [clojure.java.jdbc :as sql]
            clojure.java.jdbc.internal)) ; ensure project dep wins

(defonce ds (xa/datasource "foo" {:adapter "h2" :database "mem:foo"}))
(def spec {:datasource ds})

(sql/with-connection spec
  (sql/create-table :things
                    [:name :varchar]))

(xa/transaction
 (sql/with-connection spec
   (sql/insert-records :things {:name "success"})))

(defn read []
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things"]
      (:name (first rows)))))

;;; A web interface
(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str (read))})
(web/start "/thing" handler)
