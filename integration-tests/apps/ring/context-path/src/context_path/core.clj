(ns context-path.core
  ;; TODO: When Ring 1.2 is released, s/immutant.web/ring.middleware.resource/
  (:require [immutant.web :as web]))

(defn app [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (pr-str (dissoc (assoc request
                           :app "context-path")
                         :body))})

(def handler (web/wrap-resource app "public"))