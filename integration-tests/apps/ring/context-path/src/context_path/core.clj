(ns context-path.core
  (:require [ring.middleware.resource :as web]
            [immutant.util :as util]))

(defn app [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (pr-str (dissoc (assoc request
                           :app "context-path"
                           :app-uri (util/app-uri))
                         :body))})

(def handler (web/wrap-resource app "public"))
