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

(ns integs.cluster-help
  (:require [clj-http.client :as client]
            [jboss-as.api :as api]
            [fntest.core :refer (offset-port *server*)]))

(def http-port (partial offset-port :http))
(def messaging-port (partial offset-port 5445))
(def cookies (clj-http.cookies/cookie-store))

(defn base-url [host]
  (str "http://localhost:" (http-port host)))

(defn as-data* [method path host]
  (let [result (client/request
                {:method method
                 :url (str (base-url host) path)
                 :cookie-store cookies})]
    ;; (println "RESPONSE" result)
    {:result result
     :body (if (seq (:body result))
             (read-string (:body result)))}))

(defn get-as-data [path host]
  (:body (as-data* :get path host)))

(defn wait-for
  [pred]
  (loop [i 6000]
    (Thread/sleep 100)
    (if-let [result (pred)]
      result
      (when (< 0 i) (recur (dec i))))))

(defn mark [& msg]
  (apply println
    (.format (java.text.SimpleDateFormat. "HH:mm:ss,SSS")
      (java.util.Date.)) msg))

(defn stop [host]
  (mark "stopping" host)
  (api/stop-server (.uri *server*) host)
  (wait-for #(= "STOPPED" (api/server-status (.uri *server*) host)))
  (mark host "stopped"))

(defn start [host]
  (mark "starting" host)
  (api/start-server (.uri *server*) host)
  (wait-for #(= "STARTED" (api/server-status (.uri *server*) host)))
  (mark host "started"))
