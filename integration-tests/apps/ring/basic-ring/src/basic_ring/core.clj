(ns basic-ring.core
  (:use immutant.messaging)
  (:require [immutant.registry :as service]))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn init []
  (println "INIT CALLED"))

(defn init-messaging []
  (init)
  (start-queue "/queue/ham")
  (start-queue "/queue/biscuit")
  (processor "/queue/biscuit" #(publish "/queue/ham" (.toUpperCase %))))
  
(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value "</p>")]
    (reset! a-value "not-default")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))
