(ns testing.web
  (:require [clj-http.client :as http]
            [ring.util.response :refer [response]]))

(defn handler [body]
  (fn [request] (response body)))

(def hello (handler "hello"))

(defn get-body
  [url]
  (:body (http/get url)))
