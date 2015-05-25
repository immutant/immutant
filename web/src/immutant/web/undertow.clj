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

(ns immutant.web.undertow
  "Advanced options specific to the Undertow web server used by Immutant"
  (:require [immutant.internal.util :refer (kwargs-or-map->map)]
            [immutant.internal.options :refer (validate-options set-valid-options! opts->set coerce)]
            [immutant.web.ssl :refer (keystore->ssl-context)])
  (:import [io.undertow Undertow Undertow$Builder]
           [org.xnio Options SslClientAuthMode]
           [org.projectodd.wunderboss.web Web$CreateOption Web$RegisterOption]))

(defn tune
  "Return the passed tuning options with an Undertow$Builder instance
  set accordingly, mapped to :configuration in the return value"
  [{:keys [configuration io-threads worker-threads buffer-size buffers-per-region direct-buffers?]
    :as options}]
  (let [^Undertow$Builder builder (or configuration (Undertow/builder))]
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
  [{:keys [configuration host port ssl-port ssl-context key-managers trust-managers ajp-port]
    :or {host "localhost"}
    :as options}]
  (let [^Undertow$Builder builder (or configuration (Undertow/builder))]
    (-> options
      (assoc :configuration
        (cond-> builder
          (and ssl-port ssl-context)       (.addHttpsListener ssl-port host ssl-context)
          (and ssl-port (not ssl-context)) (.addHttpsListener ssl-port host key-managers trust-managers)
          (and ajp-port)                   (.addAjpListener ajp-port host)
          (and port)                       (.addHttpListener port host)))
      (dissoc :host :port :ssl-port :ssl-context :key-managers :trust-managers :ajp-port))))

(defn client-auth
  "Possible values are :want or :need (:requested and :required are
  also acceptable)"
  [{:keys [configuration client-auth] :as options}]
  (if client-auth
    (let [^Undertow$Builder builder (or configuration (Undertow/builder))]
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

(def options
  "Takes a map of Undertow-specific options and replaces them with an
  `Undertow$Builder` instance associated with :configuration. Three
  types of listeners are supported: :port (HTTP), :ssl-port (HTTPS), and
  :ajp-port (AJP)

  The following keyword options are supported:

   * :configuration - the Builder that, if passed, will be used

  Common to all listeners:

   * :host - the interface listener bound to, defaults to \"localhost\"

  HTTP:

   * :port - a number, for a standard HTTP listener

  AJP:

   * :ajp-port - a number, for an Apache JServ Protocol listener

  HTTPS:

   * :ssl-port - a number, requires either :ssl-context, :keystore, or :key-managers

   * :keystore - the filepath (a String) to the keystore 
   * :key-password - the password for the keystore
   * :truststore - if separate from the keystore
   * :trust-password - if :truststore passed

   * :ssl-context - a valid javax.net.ssl.SSLContext
   * :key-managers - a valid javax.net.ssl.KeyManager[]
   * :trust-managers - a valid javax.net.ssl.TrustManager[]

   * :client-auth - SSL client auth, may be :want or :need

  Tuning:

   * :io-threads - # threads handling IO, defaults to available processors
   * :worker-threads - # threads invoking handlers, defaults to (* io-threads 8)
   * :buffer-size - a number, defaults to 16k for modern servers
   * :buffers-per-region - a number, defaults to 10
   * :direct-buffers? - boolean, defaults to true"
  (comp listen ssl-context client-auth tune
    (partial coerce [:port :ajp-port :ssl-port :io-threads :worker-threads
                     :buffer-size :buffers-per-region :direct-buffers?])
    #(validate-options % options)
    kwargs-or-map->map (fn [& x] x)))

;;; take the valid options from the arglists of the composed functions
(def ^:private valid-options
  (->> [#'listen #'ssl-context #'client-auth #'tune]
    (map #(-> % meta :arglists ffirst :keys))
    flatten
    (map keyword)
    set))
(set-valid-options! options valid-options)

(def ^:private non-wunderboss-options
  (clojure.set/difference
    valid-options
    (opts->set Web$CreateOption Web$RegisterOption)))

(defn ^:no-doc ^:internal options-maybe
  [opts]
  (if (some non-wunderboss-options (keys opts))
    (options opts)
    opts))
