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
  "Provides for the creation of asynchronous WebSocket services"
  (:require [immutant.web.internal.undertow :refer [create-http-handler] :as u]
            [immutant.web.internal.servlet  :refer [create-servlet create-endpoint]]
            [immutant.web.internal.ring :as i]
            [immutant.util                  :refer [in-container?]])
  (:import [org.projectodd.wunderboss.websocket UndertowWebsocket Endpoint]
           [io.undertow.server HttpHandler]
           [javax.websocket Session]
           [javax.websocket.server HandshakeRequest]))

(defprotocol Channel
  "Websocket channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch] "Gracefully close the channel")
  (send! [ch message] "Send a message asynchronously"))

(defprotocol Handshake
  "Reflects the state of the initial websocket upgrade request"
  (headers [hs] "Return request headers")
  (parameters [hs] "Return map of params from request")
  (uri [hs] "Return full request URI")
  (query-string [hs] "Return query portion of URI")
  (session [hs] "Return the user's session data, if any")
  (user-principal [hs] "Return authorized `java.security.Principal`")
  (user-in-role? [hs role] "Is user in role identified by String?"))

(extend-protocol Channel
  io.undertow.websockets.core.WebSocketChannel
  (send! [ch message] (UndertowWebsocket/send ch message nil))
  (open? [ch] (.isOpen ch))
  (close [ch] (.sendClose ch))

  javax.websocket.Session
  (send! [ch message] (.sendObject (.getAsyncRemote ch) message))
  (open? [ch] (.isOpen ch))
  (close [ch] (.close ch)))

(extend-protocol Handshake
  io.undertow.websockets.spi.WebSocketHttpExchange
  (headers [ex] (.getRequestHeaders ex))
  (parameters [ex] (.getRequestParameters ex))
  (uri [ex] (.getRequestURI ex))
  (query-string [ex] (.getQueryString ex))
  (session [ex] (u/ring-session ex))
  (user-principal [ex] (.getUserPrincipal ex))
  (user-in-role? [ex role] (.isUserInRole ex role))

  javax.websocket.server.HandshakeRequest
  (headers [hs] (.getHeaders hs))
  (parameters [hs] (.getParameterMap hs))
  (uri [hs] (str (.getRequestURI hs)))
  (query-string [hs] (.getQueryString hs))
  (session [hs] (-> hs .getHttpSession i/ring-session))
  (user-principal [hs] (.getUserPrincipal hs))
  (user-in-role? [hs role] (.isUserInRole hs role)))

(defn wrap-websocket
  "Middleware to attach websocket callbacks to a Ring handler.
  Technically, it's \"endware\" since its return type is not a
  function, but an object expected to be passed to
  [[immutant.web/run]], so all other middleware should precede this
  call in the chain.

  The following callbacks are supported, where `channel` is an object
  extended to [[Channel]], `handshake` is extended to [[Handshake]],
  `throwable` is a Java exception, and `message` may be either a
  `String` or a `byte[]`:

    * :on-message `(fn [channel message])`
    * :on-open    `(fn [channel handshake])`
    * :on-close   `(fn [channel {:keys [code reason]}])`
    * :on-error   `(fn [channel throwable])`

  If handler is nil, 404 responses will be returned for any requests
  without `ws://` URI schemes"
  ([handler key value & key-values]
     (wrap-websocket handler (apply hash-map key value key-values)))
  ([handler {:keys [on-message on-open on-close on-error] :as callbacks}]
     (if (in-container?)
       (create-servlet handler (create-endpoint callbacks))
       (UndertowWebsocket/create
         (reify Endpoint
           (onMessage [_ channel message]
             (if on-message (on-message channel message)))
           (onOpen [_ channel exchange]
             (if on-open (on-open channel exchange)))
           (onClose [_ channel cm]
             (if on-close (on-close channel {:code (.getReason cm) :reason (.getString cm)})))
           (onError [_ channel error]
             (if on-error (on-error channel error))))
         (if handler (create-http-handler handler))))))
