(ns tx.init
  (:use clojure.test)
  (:use ring.middleware.params)
  (:require [immutant.web :as web]
            [clojure.string :as str]
            [tx.core]))

(defn handler [{params :params}]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (str (apply tx.core/testes (str/split (params "dbs") #",")))})

(web/start "/" (wrap-params handler))
