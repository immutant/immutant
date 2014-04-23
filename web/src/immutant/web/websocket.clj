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

(ns immutant.web.websocket
  (:require [immutant.logging :as log]
            [immutant.web.undertow.websocket :as undertow]
            [ring.util.response :refer [response]])
  (:import io.undertow.websockets.core.WebSocketChannel))

(defprotocol Channel
  (open? [ch])
  (close [ch])
  (send! [ch message]))

(extend-type WebSocketChannel
  Channel
  (send! [ch message] (undertow/send! ch message))
  (open? [ch] (.isOpen ch))
  (close [ch] (.sendClose ch)))

(defn create-handler
  "The following callbacks are supported:
    :on-message (fn [message])
    :on-open    (fn [channel])
    :on-close   (fn [channel {:keys [code reason]}])
    :on-error   (fn [channel throwable])
    :fallback   (fn [request] (response ...))"
  [& {:keys [on-message on-open on-close on-error fallback] :as args}]
  (undertow/create-websocket-handler args))

(defn create-logging-handler
  "A convenience fn that defaults any callbacks you don't provide to
  functions that simply log their arguments"
  [& {:as opts}]
  (undertow/create-websocket-handler
    (merge
      {:on-message (fn [message]
                     (log/info "on-message" message))
       :on-open    (fn [channel]
                     (log/info "on-open" channel))
       :on-close   (fn [channel {c :code r :reason}]
                     (log/info "on-close" channel c r))
       :on-error   (fn [channel error]
                     (log/error "on-error" channel error))
       :fallback   (fn [request]
                     (response "Unable to create websocket"))}
      opts)))
