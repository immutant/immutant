(ns basic-ring.core
  (:use immutant.messaging)
  (:require [immutant.registry :as service]))

(defn init []
  (println "INIT CALLED"))

(defn init-messaging []
  (init)
  (start-queue "/queue/ham")
  (start-queue "/queue/biscuit")
  (processor "/queue/biscuit" #(publish "/queue/ham" (.toUpperCase %))))
  
(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring")]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))
