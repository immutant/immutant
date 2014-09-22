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
  (:require [immutant.internal.util :refer (kwargs-or-map->map)]
            [immutant.web.ssl :refer (keystore->ssl-context)])
  (:import [io.undertow Undertow]
           [org.xnio Options SslClientAuthMode]))

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

(defn client-auth
  "Possible values are :want or :need (:requested and :required are
  also acceptable)"
  [{:keys [configuration client-auth] :as options}]
  (if client-auth
    (let [builder (or configuration (Undertow/builder))]
      (-> options
        (assoc :configuration
          (case client-auth
            (:want :requested) (.setSocketOption builder
                                 Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUESTED)
            (:need :required)  (.setSocketOption builder
                                 Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUIRED)))
        (dissoc :client-auth)))
    options))

(defn ssl-context
  "Assoc an SSLContext given a keystore and a trustore, which may be
  either actual KeyStore instances, or paths to them. If truststore is
  ommitted, the keystore is assumed to fulfill both roles"
  [{:keys [keystore key-password truststore trust-password] :as options}]
  (-> options
    (assoc :ssl-context (keystore->ssl-context options))
    (dissoc :keystore :key-password :truststore :trust-password)))

(defn options
  "Takes a map of {{immutant.web/run}} options that includes a subset
  of Undertow-specific ones and replaces them with an Undertow$Builder
  instance associated with :configuration"
  [& opts]
  (let [options (kwargs-or-map->map opts)]
    (-> options
      tune
      client-auth
      ssl-context
      listen)))
