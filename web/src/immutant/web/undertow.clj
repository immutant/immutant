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
  (:require [immutant.internal.util :refer [kwargs-or-map->map]])
  (:import [io.undertow Undertow]))

(defn tune
  "Return the passed tuning options with an Undertow$Builder instance
  set accordingly, mapped to :configuration in the return value"
  [{:keys [configuration io-threads worker-threads buffer-size buffers-per-region direct-buffers?]
    :as options}]
  (let [builder (or configuration (Undertow/builder))]
    (assoc options
      :configuration
      (cond-> builder
        io-threads                   (.setIoThreads io-threads)
        worker-threads               (.setWorkerThreads worker-threads)
        buffer-size                  (.setBufferSize buffer-size)
        buffers-per-region           (.setBuffersPerRegion buffers-per-region)
        (not (nil? direct-buffers?)) (.setDirectBuffers direct-buffers?)))))

(defn listen
  "Return the passed listener-related options with an Undertow$Builder
  instance set accordingly, mapped to :configuration in the return
  value. At a minimum, :host and :port are required."
  [{:keys [configuration host port ssl-context key-managers trust-managers]
    :as options}]
  (let [builder (or configuration (Undertow/builder))]
    (cond
      ssl-context  (.addHttpsListener builder port host ssl-context)
      key-managers (.addHttpsListener builder port host key-managers trust-managers)
      port         (.addHttpListener  builder port host))
    (assoc options :configuration builder)))

(defn options
  "Takes a map of {{immutant.web/run}} options that includes a subset
  of Undertow-specific ones and replaces them with an Undertow$Builder
  instance associated with :configuration"
  [& opts]
  (let [options (kwargs-or-map->map opts)]
    (-> options
      tune
      listen
      (dissoc :io-threads :worker-threads :buffer-size :buffers-per-region :direct-buffers?
        :ssl-context :key-managers :trust-managers))))
