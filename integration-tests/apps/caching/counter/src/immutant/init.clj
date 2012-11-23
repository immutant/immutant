(ns immutant.init
  (:use clojure.test)
  (:require [immutant.web :as web]
            counter.locking))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str body)})

(defn handler [request]
  (response (run-tests 'counter.locking)))

(web/start "/" handler)

