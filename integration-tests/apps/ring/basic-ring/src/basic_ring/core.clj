(ns basic-ring.core
  (:require [immutant.messaging      :as msg]
            [immutant.web            :as web]
            [immutant.web.session    :as isession]
            [immutant.repl           :as repl]
            [ring.middleware.session :as rsession]
            [clojure.java.io         :as io]))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn response [body]
  {:status 200
     :headers {"Content-Type" "text/html"}
   :body body})
(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value
                  "</p><p>version:" (clojure-version) "</p>")]
    (reset! a-value "not-default")
    (response body)))

(defn another-handler [request]
  (reset! a-value "another-handler")
  (handler request))

(defn resource-handler [request]
  (let [res (io/as-file (io/resource "a-resource"))]
    (response (slurp res))))

(defn request-echo-handler [request]
  (response (prn-str (assoc request :body "<body>")))) ;; body is a inputstream, chuck it for now

(defn init []
  (println "INIT CALLED"))


(defn init-web []
  (init)
  (web/start handler))

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

(defn init-resources []
  (init)
  (web/start resource-handler))

(defn init-request-echo []
  (init)
  (web/start request-echo-handler)
  (web/start "/foo/bar" request-echo-handler))

(defn init-nrepl []
  (init)
  (repl/start-nrepl 4321))
