(ns basic-ring.core
  (:require [immutant.messaging :as msg]
            [immutant.web :as web]
            [immutant.web.session :as isession]
            [ring.middleware.session :as rsession]))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value "</p>")]
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

(defn query-map [query-string]
  (when-not (empty? query-string)
    (apply hash-map
           (clojure.string/split query-string #"(&|=)"))))

(defn init-web-sessions []
  (init)
  (web/start "/sessions"
             (rsession/wrap-session
              (fn [request]
                (let [session (merge (:session request) (query-map (:query-string request)))]
                  (println "SESSION:" session)
                  {:status 200
                   :headers {"Content-Type" "text/html"}
                   :session session
                   :body (with-out-str (pr session))}))
               {:store (isession/servlet-store)}
              )))
