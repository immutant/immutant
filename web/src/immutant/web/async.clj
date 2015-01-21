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
  (:require [immutant.internal.options :as o]
            [immutant.internal.util    :as u])
  (:import [org.projectodd.wunderboss.web.async Channel$OnComplete HttpChannel]
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

     This will trigger the :on-close callback if one is registered. with
     [[as-channel]].")
  (handshake [ch]
    "Returns a [[WebsocketHandshake]] for `ch` if `ch` is a WebSocket channel.")
  (send* [ch message close? on-complete]
    "See [[send!]]."))

(defn send!
  "Send a message to the channel, asynchronously.

   `message` can either be a String or byte[].

   The following options are supported [default]:

   * :close? - if `true`, the channel will be closed when the send completes.
     Setting this to `true` on the first send to an HTTP stream channel
     will cause it to behave like a standard HTTP response, and *not* chunk
     the response. [false]
   * :on-complete - `(fn [throwable] ...)` - called when the send attempt
     has completed. The success of the attempt is signaled by the passed
     value. If the error requires the channel to be closed, the [[as-channel]]
     :on-close callback will also be invoked. If this callback throws
     an exception, it will be reported to the [[as-channel]] :on-error
     callback [`#(when % (throw %))`]

   Returns nil if the channel is closed when the send is initiated, true
   otherwise. If the channel is already closed, :on-complete won't be
   invoked."
  [ch message & options]
    (let [{:keys [close? on-complete]} (-> options
                                              u/kwargs-or-map->map
                                              (o/validate-options send!))]
      (send* ch message close? on-complete)))

(o/set-valid-options! send! #{:close? :on-complete})

(let [impls
      {:open? (fn [^org.projectodd.wunderboss.web.async.Channel ch] (.isOpen ch))
       :close (fn [^org.projectodd.wunderboss.web.async.Channel ch] (.close ch))
       :handshake (fn [_] nil)
       :send*
       (fn [^org.projectodd.wunderboss.web.async.Channel ch message close? on-complete]
         (.send ch message
           (boolean close?)
           (when on-complete
             (reify Channel$OnComplete
               (handle [_ error]
                 (on-complete error))))))}]
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
  Otherwise, an HTTP stream channel is created. You interact with both
  channel types through the [[Channel]] protocol, and through the
  given `callbacks`.

  The callbacks common to both channel types are:

  * :on-open - `(fn [ch] ...)` - called when the channel is
    available for sending. Will only be invoked once.
  * :on-error - `(fn [ch throwable] ...)` - Called for any error
    that occurs in relation to the channel. If the error
    requires the channel to be closed, :on-close will also be invoked.
    To handle [[send!]] errors separately, provide it a completion
    callback.
  * :on-close - `(fn [ch {:keys [code reason]}] ...)` -
    called for *any* close, including a call to [[close]], but will
    only be invoked once. `ch` will already be closed by the time
    this is invoked.

    `code` and `reason` will be the numeric closure code and text reason,
     respectively, if the channel is a WebSocket
     (see http://tools.ietf.org/html/rfc6455#section-7.4). Both will be nil
     for HTTP streams.

  If the channel is a Websocket, the following callback is also used:

  * :on-message - `(fn [ch message] ...)` - Called for each message
    from the client. `message` will be a `String` or `byte[]`

  TODO: discuss: sessions, headers, ws vs http diffs (utf8, no headers)
  provide usage example

  Returns a ring response map, at least the :body of which *must* be
  returned in the response map from the calling ring handler."
  [request & callbacks]
  (let [callbacks (-> callbacks
                    u/kwargs-or-map->map
                    (o/validate-options as-channel))
        ch (if (:websocket? request)
             (initialize-websocket request callbacks)
             (initialize-stream request callbacks))]
    {:status 200
     :body ch}))

(o/set-valid-options! as-channel
  #{:on-open :on-close :on-message :on-error})
