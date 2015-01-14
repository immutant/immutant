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

(ns immutant.web.websocket
  "Provides for the creation of asynchronous WebSocket services"
  (:require [immutant.web.internal.undertow :refer [create-http-handler]]
            [immutant.web.internal.servlet  :as servlet]
            [immutant.web.async             :as async]
            [immutant.util                  :refer [in-container?]])
  (:import [org.projectodd.wunderboss.web.async.websocket
            UndertowWebsocket WebsocketChannel WebsocketInitHandler]))

(defn wrap-websocket
  "Middleware to attach websocket callbacks to a Ring handler.
  Technically, it's \"endware\" since its return type is not a
  function, but an object expected to be passed to
  [[immutant.web/run]], so all other middleware should precede this
  call in the chain.

  The following callbacks are supported, where `channel` is an object
  extended to [[immutant.web.async/Channel]], `handshake` is extended
  to [[immutant.web.async/WebsocketHandshake]], `throwable` is a Java
  exception, and `message` may be either a `String` or a `byte[]`:

  * :on-message `(fn [channel message])`
  * :on-open    `(fn [channel handshake])`
  * :on-close   `(fn [channel {:keys [code reason]}])`
  * :on-error   `(fn [channel throwable])`

  If handler is nil, 404 responses will be returned for any requests
  without `ws://` URI schemes"
  ([handler key value & key-values]
     (wrap-websocket handler (apply hash-map key value key-values)))
  ([handler callbacks]
     (if (in-container?)
       (servlet/create-servlet handler (servlet/create-endpoint callbacks))
       (UndertowWebsocket/createHandler
         (reify WebsocketInitHandler
           (shouldConnect [_ exchange endpoint-wrapper]
             (.setEndpoint endpoint-wrapper
               (.getEndpoint ^WebsocketChannel (async/initialize-websocket {:handler-type :undertow}
                                                 callbacks)))
             true))
         (if handler (create-http-handler handler))))))
