;; Copyright 2014 Red Hat, Inc, and individual contributors.
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

(ns testing.app
  (:require [immutant.web :as web]
            [immutant.web.websocket :as ws]
            [immutant.web.middleware :refer (wrap-session)]
            [immutant.codecs :refer (encode)]
            [compojure.core :refer (GET defroutes)]
            [ring.util.response :refer (redirect response)]))

(def handshakes (atom {}))

(defn on-open-set-handshake [channel handshake]
  (let [data {:headers (ws/headers handshake)
              :parameters (ws/parameters handshake)
              :uri (ws/uri handshake)
              :query (ws/query-string handshake)
              :session (ws/session handshake)
              :user-principal (ws/user-principal handshake)}]
    (swap! handshakes assoc channel data)))

(defn on-message-send-handshake [channel message]
  (ws/send! channel (encode (get @handshakes channel))))

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str count))
      (assoc :session session))))

(defn dump
  [request]
  (response (encode (dissoc request :server-exchange :body :servlet :servlet-request :servlet-response :servlet-context))))

(defroutes routes
  (GET "/" [] counter)
  (GET "/session" {s :session} (encode s))
  (GET "/unsession" [] {:session nil})
  (GET "/request" [] dump))

(defn run []
  (web/run (-> #'routes
             wrap-session
             (ws/wrap-websocket
               :on-open #'on-open-set-handshake
               :on-message #'on-message-send-handshake))))
