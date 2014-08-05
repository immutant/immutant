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
  "Provides the creation of asynchronous Websocket services and a
  protocol through which to invoke them"
  (:require [immutant.web.internal.undertow :refer [create-http-handler]])
  (:import [org.projectodd.wunderboss.websocket UndertowWebsocket Endpoint]
           [io.undertow.server HttpHandler]))

(defprotocol Channel
  "Websocket channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch] "Gracefully close the channel")
  (send! [ch message] "Send a message asynchronously"))

(extend-protocol Channel
  io.undertow.websockets.core.WebSocketChannel
  (send! [ch message] (UndertowWebsocket/send ch message nil))
  (open? [ch] (.isOpen ch))
  (close [ch] (.sendClose ch)))

(defn ^HttpHandler wrap-websocket
  "Middleware to attach websocket callbacks to a Ring handler. It's
  more accurate to call it \"endware\" since its return type is not a
  function, but an `io.undertow.server.HttpHandler` that's expected to
  be passed to {{immutant.web/run}}, so all other middleware should
  precede this call in the chain.

  The following callbacks are supported, where `channel` is an
  instance of `io.undertow.websockets.core.WebSocketChannel`, extended
  to {{Channel}}:

    * :on-message `(fn [channel message])`
    * :on-open    `(fn [channel])`
    * :on-close   `(fn [channel {:keys [code reason]}])`
    * :on-error   `(fn [channel throwable])`

  If handler is nil, 404 responses will be returned for any requests
  without `ws` schemes"
  ([handler key value & key-values]
     (wrap-websocket handler (apply hash-map key value key-values)))
  ([handler {:keys [on-message on-open on-close on-error]}]
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
       (if handler (create-http-handler handler)))))
