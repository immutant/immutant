(ns immutant.init
  (:use clojure.test)
  (:require [immutant.web :as web]
            in-container.tests))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str body)})

(defn handler [request]
  (response (run-tests 'in-container.tests)))

(web/start "/" handler)

