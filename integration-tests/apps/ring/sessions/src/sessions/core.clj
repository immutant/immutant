(ns sessions.core
  (:require [immutant.web :as web]
            [immutant.web.session :as immutant-session]
            [immutant.util :as util]
            [ring.middleware.session :as ring-session]))

(defn query-map [query-string]
  (if-not (empty? query-string)
    (apply hash-map
           (clojure.string/split query-string #"(&|=)"))
    {}))

(defn respond [session]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :session session
   :cookies {"a-cookie" "a cookie value"}
   :body (pr-str session)})

(defn handler [request]
  (respond (merge (:session request) (query-map (:query-string request)))))

(defn session-attrs-handler [request]
  (let [rmap (query-map (:query-string request))
        sess-data (rmap "session")
        sess-attrs (if (rmap "attrs")
                     (-> (rmap "attrs")
                         ring.util.codec/url-decode
                         read-string
                         clojure.walk/keywordize-keys))]
    (util/mapply
      immutant-session/set-session-cookie-attributes! sess-attrs)
    (respond (merge (:session request)
                    (if sess-data
                      (apply hash-map (clojure.string/split sess-data #":"))
                      {})))))

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

(defn init-session-attrs []
  (web/start "/session-attrs"
             (ring-session/wrap-session
              session-attrs-handler
              {:store (immutant-session/servlet-store)})))

(defn init-all []
  (let [ring-mem-store (ring.middleware.session.memory/memory-store)]
    (init-ring-session ring-mem-store)
    (init-immutant-session)
    (init-ring-clearer ring-mem-store)
    (init-immutant-clearer)))
