(ns sessions.core
  (:require [immutant.web :as web]
            [immutant.web.session :as immutant-session]
            [ring.middleware.session :as ring-session]))

(defn query-map [query-string]
  (when-not (empty? query-string)
    (apply hash-map
           (clojure.string/split query-string #"(&|=)"))))

(defn respond [session]
  ;(println "SESSION:" session)
  {:status 200
   :headers {"Content-Type" "text/html"}
   :session session
   :cookies {"a-cookie" "a cookie value"}
   :body (pr-str session)})

(defn handler [request]
  (respond (merge (:session request) (query-map (:query-string request)))))

(defn init-immutant-session []
  (web/start "/immutant"
             (ring-session/wrap-session
              handler
              {:store (immutant-session/servlet-store)})))


(defn init-ring-session [store]
  (web/start "/ring"
             (ring-session/wrap-session
              handler
              {:store store})))

(defn clear-handler
  [request]
  (respond nil))

(defn init-ring-clearer [store]
  (web/start "/clear-ring"
             (ring-session/wrap-session
              clear-handler
              {:store store})))

(defn init-immutant-clearer []
  (web/start "/clear"
             (ring-session/wrap-session
              clear-handler
              {:store (immutant-session/servlet-store)})))

(defn init-all []
  (let [ring-mem-store (ring.middleware.session.memory/memory-store)]
    (init-ring-session ring-mem-store)
    (init-immutant-session)
    (init-ring-clearer ring-mem-store)
    (init-immutant-clearer)))
