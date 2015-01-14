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

(ns immutant.web.ssl
  "A few SSL-related utilities, typically invoked via [[immutant.web.undertow/options]]"
  (:require [clojure.java.io :as io])
  (:import [java.security KeyStore]
           [javax.net.ssl SSLContext KeyManagerFactory TrustManagerFactory]))

(defn- ^KeyStore load-keystore
  [keystore ^String password]
  (if (string? keystore)
    (with-open [in (io/input-stream keystore)]
      (doto (KeyStore/getInstance (KeyStore/getDefaultType))
        (.load in (.toCharArray password))))
    keystore))

(defn keystore->key-managers
  "Return a KeyManager[] given a KeyStore and password"
  [^KeyStore keystore ^String password]
  (.getKeyManagers
    (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
      (.init keystore (.toCharArray password)))))

(defn truststore->trust-managers
  "Return a TrustManager[] for a KeyStore"
  [^KeyStore truststore]
  (.getTrustManagers
    (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
      (.init truststore))))

(defn ^SSLContext keystore->ssl-context
  "Turn a keystore and optional truststore, which may be either
  strings denoting file paths or actual KeyStore instances, into an
  SSLContext instance"
  [{:keys [keystore key-password truststore trust-password]}]
  (when keystore
    (let [ks (load-keystore keystore key-password)
          ts (if truststore
               (load-keystore truststore trust-password)
               ks)]
      (doto (SSLContext/getInstance "TLS")
        (.init
          (keystore->key-managers ks key-password)
          (truststore->trust-managers ts)
          nil)))))
