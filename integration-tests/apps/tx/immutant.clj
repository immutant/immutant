(ns tx.init
  (:use clojure.test)
  (:use ring.middleware.params)
  (:require [immutant.web :as web]
            [clojure.string :as str]
            [tx.core]))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str body)})

(defn handler [{params :params}]
  (response (apply tx.core/explicit-transaction-testes (str/split (params "dbs") #","))))

(web/start "/" (wrap-params handler))

(defn listen-handler [{params :params}]
  (response (apply tx.core/listen-testes (str/split (params "dbs") #","))))

(web/start "/listen" (wrap-params listen-handler))
