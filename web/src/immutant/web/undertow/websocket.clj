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

(ns ^{:no-doc true} immutant.web.undertow.websocket
  (:require [immutant.logging :as log]
            [immutant.web.undertow.http :refer [create-http-handler]])
  (:import [java.nio ByteBuffer]
           [io.undertow.websockets WebSocketProtocolHandshakeHandler WebSocketConnectionCallback]
           [io.undertow.websockets.spi WebSocketHttpExchange]
           [io.undertow.websockets.core WebSocketChannel WebSockets AbstractReceiveListener
            BufferedTextMessage BufferedBinaryMessage CloseMessage]
           [io.undertow.server.handlers ResponseCodeHandler]))

(defn- bytes? [x] (= Byte/TYPE (.getComponentType (class x))))

(defn send!
  [^WebSocketChannel channel message & [callback]]
  (cond
    (string? message)
    (WebSockets/sendText message channel callback)
    (bytes? message)
    (WebSockets/sendBinary (ByteBuffer/wrap message) channel callback)
    :else (throw (IllegalArgumentException. "Message must be a String or byte[]"))))

(defn- callback
  [f & args]
  (when f (apply f args)))

(defn- callback-binary-message
  "TODO: figure out messages across multiple ByteBuffers"
  [on-message ^WebSocketChannel channel, ^BufferedBinaryMessage message]
  (when on-message
    (let [data (.getData message)]
      (try
        (let [payload (.getResource data)
              buffer (first payload)]
          (if (and buffer (.hasArray buffer) (= 1 (count payload)))
            (on-message channel (.array buffer))
            (throw (UnsupportedOperationException. "TODO: binary messages across multiple ByteBuffers"))))
        (finally
          (.free data))))))

(defn create-websocket-handler
  "The following callbacks are supported:
    :on-message (fn [channel message])
    :on-open    (fn [channel])
    :on-close   (fn [channel {:keys [code reason]}])
    :on-error   (fn [channel throwable])
    :fallback   (fn [request] (response ...))"
  [{:keys [on-message on-open on-close on-error fallback]}]
  (WebSocketProtocolHandshakeHandler.
    (reify WebSocketConnectionCallback
      (^void onConnect [_ ^WebSocketHttpExchange exchange ^WebSocketChannel channel]
        (callback on-open channel)
        (.. channel getReceiveSetter
          (set (proxy [AbstractReceiveListener] []
                 (onError [^WebSocketChannel channel, ^Throwable error]
                   (callback on-error channel error)
                   (proxy-super onError channel error))
                 (onCloseMessage [^CloseMessage cm, ^WebSocketChannel channel]
                   (callback on-close channel {:code (.getCode cm) :reason (.getReason cm)}))
                 (onFullTextMessage [^WebSocketChannel channel, ^BufferedTextMessage message]
                   (callback on-message channel (.getData message)))
                 (onFullBinaryMessage [^WebSocketChannel channel, ^BufferedBinaryMessage message]
                   (callback-binary-message on-message channel message)))))
        (.resumeReceives channel)))
    (if fallback (create-http-handler fallback) ResponseCodeHandler/HANDLE_404)))
