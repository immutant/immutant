;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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
            [immutant.web.async :as async]
            [immutant.web.middleware :refer (wrap-session wrap-websocket)]
            [immutant.codecs :refer (encode)]
            [compojure.core :refer (GET defroutes)]
            [ring.util.response :refer (charset redirect response)]))

(def handshakes (atom {}))

(defn on-open-set-handshake [channel handshake]
  (let [data {:headers (async/headers handshake)
              :parameters (async/parameters handshake)
              :uri (async/uri handshake)
              :query (async/query-string handshake)
              :session (async/session handshake)
              :user-principal (async/user-principal handshake)}]
    (swap! handshakes assoc channel data)))

(defn on-message-send-handshake [channel message]
  (async/send! channel (encode (get @handshakes channel))))

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str count))
      (assoc :session session))))

(defn dump
  [request]
  (response (encode (dissoc request :server-exchange :body :servlet :servlet-request :servlet-response :servlet-context))))

(defn with-charset [request]
  (let [[_ cs] (re-find #"charset=(.*)" (:query-string request))
        body "気になったら"]
    (charset {:headers {"BodyBytes" (pr-str (into [] (.getBytes body cs)))}
              :body body}
      cs)))

(defn chunked-stream [request]
  (update-in
    (async/as-channel request
      {:on-open
       (fn [stream]
         (.start
           (Thread.
             (fn []
               (async/send! stream "[" false)
               (dotimes [n 10]
                 ;; we have to send a few bytes with each
                 ;; response - there is a min-bytes threshold to
                 ;; trigger data to the client
                 (async/send! stream (format "%s ;; %s\n" n (repeat 128 "1")) false))
               ;; 2-arity send! closes the stream
               (async/send! stream "]")))))})
    [:headers] assoc "ham" "biscuit"))

(defn non-chunked-stream [request]
  (async/as-channel request
    {:on-open
     (fn [stream]
       (async/send! stream (str (repeat 128 "1"))))}))

(defn ws-as-channel
  [request]
  (assoc
    (async/as-channel request
      {:on-open (fn [ch hs]
                  #_(println "TC: open" ch hs))
       :on-message (fn [ch message]
                     #_(println "TC: message" message)
                     (async/send! ch (.toUpperCase message)))
       :on-error (fn [ch err]
                   (println "Error on websocket")
                   (.printStackTrace err))
       :on-close (fn [ch reason]
                   #_(println "TC: closed" reason))})
    :session (assoc (:session request) :ham :sandwich)))

(defroutes routes
  (GET "/" [] counter)
  (GET "/session" {s :session} (encode s))
  (GET "/unsession" [] {:session nil})
  (GET "/request" [] dump)
  (GET "/charset" [] with-charset)
  (GET "/chunked-stream" [] chunked-stream)
  (GET "/non-chunked-stream" [] non-chunked-stream))

(defn run []
  (web/run (-> #'routes
             (wrap-websocket
               :on-open #'on-open-set-handshake
               :on-message #'on-message-send-handshake)
             wrap-session))
  (web/run (-> ws-as-channel wrap-session) :path "/ws"))
