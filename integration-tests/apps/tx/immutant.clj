(ns tx.init
  (:use clojure.test)
  (:use ring.middleware.params)
  (:require [immutant.web :as web]
            [clojure.string :as str]
            [tx.core]))

(defn handler [{params :params}]
  (tx.core/with-databases (str/split (params "dbs") #",")
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str (run-tests 'tx.core))}))
(web/start "/" (wrap-params handler))
