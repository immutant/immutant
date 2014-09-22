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

(ns immutant.web.undertow
  "Advanced options specific to the Undertow web server used by Immutant"
  (:require [clojure.java.io :as io]
            [immutant.internal.util :refer [kwargs-or-map->map]])
  (:import [io.undertow Undertow]
           [java.security KeyStore]
           [javax.net.ssl SSLContext KeyManagerFactory TrustManagerFactory]))

(defn tune
  "Return the passed tuning options with an Undertow$Builder instance
  set accordingly, mapped to :configuration in the return value"
  [{:keys [configuration io-threads worker-threads buffer-size buffers-per-region direct-buffers?]
    :as options}]
  (let [builder (or configuration (Undertow/builder))]
    (-> options
      (assoc :configuration
        (cond-> builder
          io-threads                   (.setIoThreads io-threads)
          worker-threads               (.setWorkerThreads worker-threads)
          buffer-size                  (.setBufferSize buffer-size)
          buffers-per-region           (.setBuffersPerRegion buffers-per-region)
          (not (nil? direct-buffers?)) (.setDirectBuffers direct-buffers?)))
      (dissoc :io-threads :worker-threads :buffer-size :buffers-per-region :direct-buffers?))))

(defn listen
  "Return the passed listener-related options with an Undertow$Builder
  instance set accordingly, mapped to :configuration in the return
  value. If :ssl-port is non-nil, either :ssl-context or :key-managers
  should be set, too"
  [{:keys [configuration host port ssl-port ssl-context key-managers trust-managers]
    :or {host "localhost"}
    :as options}]
  (when (and ssl-port (every? nil? [ssl-context key-managers]))
    (throw (IllegalArgumentException. "Either :ssl-context or :key-managers is required for SSL")))
  (let [builder (or configuration (Undertow/builder))]
    (-> options
      (assoc :configuration
        (cond-> builder
          (and ssl-port ssl-context)  (.addHttpsListener ssl-port host ssl-context)
          (and ssl-port key-managers) (.addHttpsListener ssl-port host key-managers trust-managers)
          port (.addHttpListener port host)))
      (dissoc :host :port :ssl-port :ssl-context :key-managers :trust-managers))))

(defn- ^KeyStore load-keystore
  "TODO: maybe factor these private fns into immutant.web.ssl?"
  [keystore password]
  (if (string? keystore)
    (with-open [in (io/input-stream keystore)]
      (doto (KeyStore/getInstance (KeyStore/getDefaultType))
        (.load in (.toCharArray password))))
    keystore))

(defn- keystore->key-managers
  [^KeyStore keystore ^String password]
  (.getKeyManagers
    (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
      (.init keystore (.toCharArray password)))))

(defn- truststore->trust-managers
  [^KeyStore truststore]
  (.getTrustManagers
    (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
      (.init truststore))))

(defn keystore->ssl-context
  "Assoc an SSLContext given a keystore and a trustore, which may be
  actual KeyStore instances, or paths to them. If truststore is
  ommitted, the keystore is assumed to fulfill both roles"
  [{:keys [keystore key-password truststore trust-password] :as options}]
  (-> options
    (assoc :ssl-context
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
    (dissoc :keystore :key-password :truststore :trust-password)))

(defn options
  "Takes a map of {{immutant.web/run}} options that includes a subset
  of Undertow-specific ones and replaces them with an Undertow$Builder
  instance associated with :configuration"
  [& opts]
  (let [options (kwargs-or-map->map opts)]
    (-> options
      tune
      keystore->ssl-context
      listen)))
