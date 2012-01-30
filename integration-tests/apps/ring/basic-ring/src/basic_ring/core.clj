(ns basic-ring.core
  (:require [immutant.messaging :as msg]
            [immutant.web :as web]
            [immutant.web.session :as isession]
            [ring.middleware.session :as rsession]))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value
                  "</p><p>version:" (clojure-version) "</p>")]
    (reset! a-value "not-default")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))

(defn another-handler [request]
  (reset! a-value "another-handler")
  (handler request))

(defn init []
  (println "INIT CALLED"))


(defn init-web []
  (init)
  (web/start "/" handler))

(defn init-messaging []
  (init)
  (msg/start "/queue/ham")
  (msg/start "/queue/biscuit")
  (msg/listen "/queue/biscuit" #(msg/publish "/queue/ham" (.toUpperCase %))))

(defn init-web-start-testing []
  (init)
  (web/start "/stopper"
             (fn [r]
               (web/stop "/stopper")
               (handler r))))

