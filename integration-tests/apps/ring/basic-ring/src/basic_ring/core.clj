(ns basic-ring.core
  (:require [immutant.messaging :as msg])
  (:require [immutant.registry :as service]))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn init []
  (println "INIT CALLED"))

(defn init-messaging []
  (init)
  (msg/start "/queue/ham")
  (msg/start "/queue/biscuit")
  (msg/listen "/queue/biscuit" #(msg/publish "/queue/ham" (.toUpperCase %))))
  
(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value "</p>")]
    (reset! a-value "not-default")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))
