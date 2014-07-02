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
  "Provides the creation of asynchronous Websocket services deployed
  as either an Undertow HttpHandler ({{create-handler}}) or a JSR 356
  Endpoint ({{create-servlet}})"
  (:require [immutant.logging :as log]
            [immutant.web.undertow :refer [create-http-handler]]
            [immutant.web.javax :as javax])
  (:import [org.projectodd.wunderboss.websocket UndertowWebsocket Endpoint]))

(defprotocol Channel
  "Websocket channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch] "Gracefully close the channel")
  (send! [ch message] "Send a message asynchronously"))

(extend-protocol Channel

  io.undertow.websockets.core.WebSocketChannel
  (send! [ch message] (UndertowWebsocket/send ch message nil))
  (open? [ch] (.isOpen ch))
  (close [ch] (.sendClose ch))

  javax.websocket.Session
  (send! [ch message] (.sendObject (.getAsyncRemote ch) message))
  (open? [ch] (.isOpen ch))
  (close [ch] (.close ch)))

(defn create-handler
  "The following callbacks are supported, where `channel` is an instance
  of io.undertow.websockets.core.WebSocketChannel:

    * :on-message `(fn [channel message])`
    * :on-open    `(fn [channel])`
    * :on-close   `(fn [channel {:keys [code reason]}])`
    * :on-error   `(fn [channel throwable])`
    * :fallback   `(fn [request] (response ...))`"
  ([key value & key-values]
     (create-handler (apply hash-map key value key-values)))
  ([{:keys [on-message on-open on-close on-error fallback]}]
     (UndertowWebsocket/create
       (reify Endpoint
         (onMessage [_ channel message]
           (if on-message (on-message channel message)))
         (onOpen [_ channel exchange]
           (if on-open (on-open channel)))
         (onClose [_ channel cm]
           (if on-close (on-close channel {:code (.getReason cm) :reason (.getString cm)})))
         (onError [_ channel error]
           (if on-error (on-error channel error))))
       (if fallback (create-http-handler fallback)))))

(defn attach-endpoint
  "The same callbacks accepted by {{create-handler}} are supported,
  except for :fallback which is obviated by the required servlet
  parameter. The {{Channel}} passed to the callbacks will be an
  instance of `javax.websocket.Session`.

  In addition, a :path may be specified. It will be resolved relative
  to the path on which the returned servlet is mounted.

  Finally, if a :handshake callback is passed, it will be used as the
  `modifyHandshake` method on a `ServerEndpointConfig$Configurator`
  instance. Because this is often used as a means to obtain the
  handshake's request headers or associated servlet's session, we
  store the `HandshakeRequest` parameter passed to `modifyHandshake`
  in the map returned by `Session.getUserProperties` by default, i.e.
  when :handshake is *not* passed"
  ([servlet key value & key-values]
     (attach-endpoint servlet (apply hash-map key value key-values)))
  ([servlet {:keys [path on-message on-open on-close on-error handshake] :as args}]
     (javax/attach-endpoint servlet (javax/create-endpoint args) args)))
