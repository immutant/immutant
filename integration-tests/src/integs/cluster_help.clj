;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;;
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;;
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;;
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns integs.cluster-help
  (:require [clj-http.client :as client]
            [jboss-as.api :as api]
            [fntest.core :refer (offset-port *server*)]))

(def http-port (partial offset-port :http))
(def messaging-port (partial offset-port :messaging))
(def ^:dynamic *cookies* (clj-http.cookies/cookie-store))

(defn base-url [host]
  (str "http://localhost:" (http-port host)))

(defn as-data* [method path host]
  (let [result (client/request
                {:method method
                 :url (str (base-url host) path)
                 :cookie-store *cookies*})]
    ;; (println "RESPONSE" result)
    {:result result
     :body (if (seq (:body result))
             (read-string (:body result)))}))

(defn get-as-data [path host]
  (:body (as-data* :get path host)))

(defn wait-for
  [pred]
  (loop [i 3000]
    (Thread/sleep 100)
    (if-let [result (pred)]
      result
      (when (< 0 i) (recur (dec i))))))

(defn mark [& msg]
  (apply println
    (.format (java.text.SimpleDateFormat. "HH:mm:ss,SSS")
      (java.util.Date.)) msg))

(defn wait-for-ready [f path server]
  (if (wait-for
        #(try
           (f (binding [*cookies* nil]
                (get-as-data path server)))
           (catch Exception e
             (let [data (ex-data e)]
               (when-not (or (instance? java.net.ConnectException e)
                           (and
                             data
                             (= 404 (get-in data [:object :status]))))
                 (throw e))))))
    (mark server "ready")
    (mark "gave up waiting for" server "to be ready")))

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
