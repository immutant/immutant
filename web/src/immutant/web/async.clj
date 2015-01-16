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

(ns immutant.web.async
  (:import [org.projectodd.wunderboss.web.async HttpChannel]
           [org.projectodd.wunderboss.web.async.websocket WebsocketChannel]))

(defn ^:internal streaming-body? [body]
  (instance? HttpChannel body))

(defn ^:internal open-stream [^HttpChannel channel headers]
  (.notifyOpen channel nil))

(defmulti ^:internal initialize-stream :handler-type)

(defmulti ^:internal initialize-websocket :handler-type)

(defprotocol WebsocketHandshake
  "Reflects the state of the initial websocket upgrade request"
  (headers [hs] "Return request headers")
  (parameters [hs] "Return map of params from request")
  (uri [hs] "Return full request URI")
  (query-string [hs] "Return query portion of URI")
  (session [hs] "Return the user's session data, if any")
  (user-principal [hs] "Return authorized `java.security.Principal`")
  (user-in-role? [hs role] "Is user in role identified by String?"))

(defprotocol Channel
  "Streaming channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch]
    "Gracefully close the channel.

     This will trigger the on-close callback for the channel if one is
     registered.")
  (handshake [ch] "Returns a [[WebsocketHandshake]] for `ch` if `ch` is a WebSocket channel.")
  (send! [ch message] [ch message close?]
    "Send a message to the channel.

     If close? is truthy, close the channel after writing. close?
     defaults to false for WebSockets, true otherwise.

     Sending is asynchronous for WebSockets, but blocking for
     HTTP channels.

     Returns nil if the channel is closed, true otherwise."))

(let [impls
      {:open? (fn [^org.projectodd.wunderboss.web.async.Channel ch] (.isOpen ch))
       :close (fn [^org.projectodd.wunderboss.web.async.Channel ch] (.close ch))
       :handshake (fn [_] nil)
       :send! (fn ([^org.projectodd.wunderboss.web.async.Channel ch message]
                  ;; TODO: support codecs? support the same functionality as ring bodies?
                  (.send ch message))
                ([^org.projectodd.wunderboss.web.async.Channel ch message close?]
                 (.send ch message close?)))}]
  (extend org.projectodd.wunderboss.web.async.Channel
    Channel
    impls)

  (extend WebsocketChannel
    Channel
    (assoc impls
      :handshake (fn [^WebsocketChannel ch] (.handshake ch)))))

(defn as-channel
  "Converts the current ring `request` in to an asynchronous channel.

  The type of channel created depends on the request - if the request
  is a Websocket upgrade request, a Websocket channel will be created.
  Otherwise, an HTTP channel is created. You interact with both
  channel types through the [[Channel]] protocol, and through the
  given `callbacks`.

  The callbacks common to both channel types are:

  * `:on-open` - `(fn [ch] ...)`
  * `:on-close` - `(fn [ch reason] ...)` - invoked after close. TODO: make reason consistent

  If the channel is a Websocket, the following callbacks are also used:

  * `:on-message` - `(fn [ch message] ...)` - String or byte[]
  * `:on-error` - `(fn [ch throwable] ...)`

  The channel won't be available for writing until the `:on-open`
  callback is invoked.

  discuss: sessions, headers
  provide usage example

  Returns a ring response map, at least the :body of which *must* be
  returned in the response map from the calling ring handler."
  [request {:keys [on-open on-close on-message on-error] :as callbacks}]
  (let [ch (if (:websocket? request)
             (initialize-websocket request callbacks)
             (initialize-stream request callbacks))]
    {:status 200
     :body ch}))
