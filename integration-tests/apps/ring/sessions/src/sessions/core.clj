(ns sessions.core
  (:require [immutant.web :as web]
            [immutant.web.session :as immutant-session]
            [ring.middleware.session :as ring-session]))

(defn query-map [query-string]
  (when-not (empty? query-string)
    (apply hash-map
           (clojure.string/split query-string #"(&|=)"))))

(defn init []
  (println "INIT CALLED"))

(defn handler [request]
  (let [session (merge (:session request) (query-map (:query-string request)))]
    (println "SESSION:" session)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :session session
     :cookies {"a-cookie" "a cookie value"}
     :body (with-out-str (pr session))}))

(defn init-immutant-session []
  (init)
  (web/start "/immutant"
             (ring-session/wrap-session
              handler
              {:store (immutant-session/servlet-store)})))


(defn init-ring-session []
  (init)
  (web/start "/ring"
             (ring-session/wrap-session
              handler)))

(defn init-all []
  (init-ring-session)
  (init-immutant-session))
