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

(ns immutant.websocket
  (:require [immutant.logging :as log]
            [immutant.web.undertow.websocket :as undertow]
            [immutant.web.javax :as javax]
            [ring.util.response :refer [response]]))

(defprotocol Channel
  "Websocket channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch] "Gracefully close the channel")
  (send! [ch message] "Send a message asynchronously"))

(extend-protocol Channel

  io.undertow.websockets.core.WebSocketChannel
  (send! [ch message] (undertow/send! ch message))
  (open? [ch] (.isOpen ch))
  (close [ch] (.sendClose ch))

  javax.websocket.Session
  (send! [ch message] (.sendObject (.getAsyncRemote ch) message))
  (open? [ch] (.isOpen ch))
  (close [ch] (.close ch)))

(defn create-handler
  "The following callbacks are supported, where Channel is an instance
  of io.undertow.websockets.core.WebSocketChannel:

    :on-message (fn [channel message])
    :on-open    (fn [channel])
    :on-close   (fn [channel {:keys [code reason]}])
    :on-error   (fn [channel throwable])
    :fallback   (fn [request] (response ...))"
  [& {:keys [on-message on-open on-close on-error fallback] :as args}]
  (undertow/create-websocket-handler args))

(defn create-servlet
  "The same callbacks accepted by create-handler are supported, where
  the Channel passed to the callbacks is an instance of
  javax.websocket.Session

  In addition, a :path may be specified. It will be resolved relative
  to the :context-path on which the returned servlet is mounted"
  [& {:keys [path on-message on-open on-close on-error fallback] :as args}]
  (javax/create-endpoint-servlet args))
