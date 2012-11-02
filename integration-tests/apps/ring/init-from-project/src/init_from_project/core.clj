(ns init-from-project.core
  (:require [immutant.web :as web]))

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (pr-str body)})

(defn handler [request]
  (response "init-from-project"))

(defn init-web []
  (web/start handler))
