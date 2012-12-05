(ns immutant.init
  (:use clojure.test)
  (:require [immutant.web :as web]
            in-container.messaging
            in-container.pipeline))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str body)})

(defn handler [request]
  (response (run-tests 'in-container.messaging 'in-container.pipeline)))

(web/start "/" handler)

