;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns immutant.integs.integ-helper
  (:require [clj-http.client :as client])
  (:use [immutant.integs :only (modes)]))

(def deployment-class-loader-regex
  #"ImmutantClassLoader.*deployment\..*\.clj")

(def port-offset 67)

(defn offset? []
  (boolean (some #{:offset} modes)))

(defn offset-port [port]
  (if (offset?)
    (+ port port-offset)
    port))

(def http-port (partial offset-port 8080))

(defn base-url []
  (str "http://localhost:" (http-port)))

(def hornetq-port (partial offset-port 5445))

(def remoting-port (partial offset-port 9999))

(defn remote [f & args]
  (apply f (concat args
                   (if-not (some #{:host} args)
                     [:host "localhost"])
                   [:port (hornetq-port)])))

(defn as-data* [method path & [opts]]
  (let [result (client/request
                (merge
                 {:method method
                  :url (str (base-url) path)}
                 opts))]
    ;;(println "RESPONSE" result)
    {:result result
     :body (if (seq (:body result))
             (read-string (:body result)))}))

(defn as-data [method path & [opts]]
  (:body (as-data* method path opts)))

(defn get-as-data* [path & [opts]]
  (as-data* :get path opts))

(defn get-as-data [path]
  (:body (get-as-data* path)))
