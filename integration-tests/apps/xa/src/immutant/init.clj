(ns immutant.init
  (:use ring.middleware.params)
  (:require [immutant.xa :as xa]
            [immutant.web :as web]
            [clojure.java.jdbc :as sql]))

;;; The only reason we're doing this!
(def ds (xa/datasource "foo" {:adapter "h2" :database "mem:foo"}))

;;; The connection spec required by clojure.java.jdbc
(def spec {:datasource ds})

;;; Some sample fruit
(sql/with-connection spec
  (sql/create-table :things
                    [:id :serial]
                    [:name :varchar])
  (sql/insert-records :things
                      {:name "apple"}
                      {:name "peach"}
                      {:name "grape"}))

;;; A finder
(defn find-thing
  [id]
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things where id=?" id]
      (:name (first rows)))))

;;; A web interface
(defn handler [{params :params}]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str (find-thing (params "id")))})
(web/start "/thing" (wrap-params handler))
