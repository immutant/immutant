;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns testing.web
  (:require [http.async.client :as http]
            [ring.util.response :refer [response]])
  (:import [org.glassfish.jersey.media.sse EventSource EventListener]
           [javax.ws.rs.client ClientBuilder]))

(def cookies (atom nil))

(defn handler [body]
  (fn [request] (response body)))

(def hello (handler "hello"))

(defn get-response
  "Return the response as a map. Any response returning a :set-cookie
  will cause subsequent requests to send them. The raw
  ByteArrayOutputStream of the body is included as :raw-body]"
  [url & {:keys [headers cookies query] :or {cookies @testing.web/cookies}}]
  (with-open [client (http/create-client)]
    (let [response (http/GET client url :headers headers :cookies cookies :query query)]
      (http/await response)
      (when-let [error (http/error response)]
        (throw error))
      (when (contains? (http/headers response) :set-cookie)
        (reset! testing.web/cookies (http/cookies response)))
      {:status (:code (http/status response))
       :headers (http/headers response)
       :raw-body @(:body response)
       :body (http/string response)})))

(defn get-body
  "Return the response body as a string if status=200, otherwise return
  the numeric status code. Any response returning a :set-cookie will
  cause subsequent requests to send them."
  [url & {:keys [headers cookies] :or {cookies @testing.web/cookies}}]
  (with-open [client (http/create-client)]
    (let [response (get-response url :headers headers :cookies cookies)]
      (if (= 200 (:status response))
        (:body response)
        (do
          (if-let [b (:body response)] (println b))
          (:status response))))))

(defn event-source
  "Returns an SSE EventSource client"
  [url]
  (-> (EventSource/target 
        (-> (ClientBuilder/newBuilder)
          .build
          (.target url)))
    .build))

(defn handle-events
  "Register the event handler with an EventSource"
  [source f]
  (doto source
    (.register (reify EventListener
                 (onEvent [_ e]
                   (f e))))))
