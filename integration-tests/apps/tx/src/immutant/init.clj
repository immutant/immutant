(ns immutant.init
  (:use clojure.test)
  (:use ring.middleware.params)
  (:require [immutant.web :as web]
            [clojure.string :as str]
            [tx core scope listen korma]))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str body)})

(defn handler [{params :params}]
  (apply tx.core/testes (str/split (params "dbs") #","))
  (response (run-tests 'tx.core 'tx.scope 'tx.listen 'tx.korma)))

(web/start "/" (wrap-params handler))

