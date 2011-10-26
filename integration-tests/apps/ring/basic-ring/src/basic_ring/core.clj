(ns basic-ring.core
  (:use immutant.messaging)
  (:require [immutant.registry :as service]))

(defn init []
  (println "INIT CALLED"))

(defn handler [request]
  (if (.endsWith (:uri request) "process")
    (processor "/queue/biscuit" #(publish "/queue/ham" (.toUpperCase %))))
  (let [body (str "Hello from Immutant! This is basic-ring")
        factory (service/service "jboss.naming.context.java.ConnectionFactory")]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str body "<p>" factory "</p>")}))
