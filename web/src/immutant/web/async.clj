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
  "Provides a common interface for WebSockets and HTTP streaming."
  (:require [immutant.internal.options :as o]
            [immutant.internal.util    :as u])
  (:import [org.projectodd.wunderboss.web.async Channel Channel$OnComplete HttpChannel]
           [org.projectodd.wunderboss.web.async.websocket WebsocketChannel]
           [java.io File FileInputStream InputStream]
           [java.util Arrays Map]
           clojure.lang.ISeq))

(defn ^:internal ^:no-doc streaming-body? [body]
  (instance? HttpChannel body))

(defn ^:internal ^:no-doc open-stream [^HttpChannel channel response-map
                                       set-status-fn set-headers-fn]
  (doto channel
    (.attach :response-map response-map)
    (.attach :set-status-fn set-status-fn)
    (.attach :set-headers-fn set-headers-fn)
    (.notifyOpen nil)))

(defmulti ^:internal ^:no-doc initialize-stream :handler-type)

(defmulti ^:internal ^:no-doc initialize-websocket :handler-type)

(defprotocol ^:private MessageDispatch
  (dispatch-message [from ch options-map]))

(defn ^:private notify
  ([ch callback]
   (notify ch callback nil))
  ([^Channel ch callback e]
   (if callback
     ;; catch the case where the callback itself throws,
     ;; and notify the channel callback instead of letting it
     ;; bubble up, since that may trigger the same callback
     ;; being called again
     (try
       (if e
         (callback e)
         (callback))
       (catch Throwable e'
         (.notifyError ch e')))
     (when e (.notifyError ch e)))
   ::notified))

(defmacro ^:private catch-and-notify [ch on-error & body]
  `(try
     ~@body
     (catch Throwable e#
       (notify ~ch ~on-error e#))))

(def ^:dynamic ^:private *dispatched?* nil)

(defmacro ^:private maybe-dispatch [& body]
  `(if *dispatched?*
     (do ~@body)
     (binding [*dispatched?* true]
       (future ~@body))))

(defn ^:private finalize-channel-response
  [^Channel ch status headers]
  (when (and (instance? HttpChannel ch)
          (not (.sendStarted ^HttpChannel ch)))
    (let [orig-response (.get ch :response-map)]
      ((.get ch :set-status-fn) (or status (:status orig-response)))
      ((.get ch :set-headers-fn) (or headers (:headers orig-response))))))

(defn ^:private wboss-send [^Channel ch message options]
  (let [{:keys [close? on-success on-error status headers]} options]
    (finalize-channel-response ch status headers)
    (.send ch message
      (boolean close?)
      (when (or on-success on-error)
        (reify Channel$OnComplete
          (handle [_ error]
            (if (and error on-error)
              (on-error error)
              (when on-success (on-success)))))))))

(defn originating-request
  "Returns the request map for the request that initiated the channel."
  [^Channel ch]
  (.get ch :originating-request))

(defn open?
  "Is the channel open?"
  [^Channel ch]
  (.isOpen ch))

(defn close
  "Gracefully close the channel.

   This will trigger the :on-close callback if one is registered. with
   [[as-channel]]."
  [^Channel ch]
  (finalize-channel-response ch nil nil)
  (.close ch))

(extend-protocol MessageDispatch
  Object
  (dispatch-message [message _ _]
    (throw (IllegalStateException. (str "Can't send message of type " (class message)))))

  nil
  (dispatch-message [_ ch options]
    (wboss-send ch nil options))

  Map
  (dispatch-message [message ch options]
    (when (not (instance? HttpChannel ch))
      (throw (IllegalArgumentException. "Can't send map: channel is not an HTTP stream channel")))
    (when (.sendStarted ^HttpChannel ch)
      (throw (IllegalArgumentException. "Can't send map: this is not the first send to the channel")))
    (dispatch-message (:body message) ch
      (merge options (select-keys message [:status :headers]))))

  String
  (dispatch-message [message ch options]
    (wboss-send ch message options))

  ISeq
  (dispatch-message [message ch {:keys [on-success on-error close?] :as options}]
    (maybe-dispatch
      (let [result (catch-and-notify ch on-error
                     (loop [item (first message)
                            items (rest message)]
                       (let [latch (promise)]
                         (dispatch-message item ch
                           (assoc options
                             :on-success #(deliver latch nil)
                             :on-error   (partial deliver latch)
                             :close?     false))
                         (if-let [err @latch]
                           (notify ch on-error err)
                           (when (seq items)
                             (recur (first items) (rest items)))))))]
        (when-not (= ::notified result)
          (notify ch on-success)))
      (when close?
        (close ch))))

  File
  (dispatch-message [message ch options]
    (dispatch-message (FileInputStream. message) ch options))

  InputStream
  (dispatch-message [message ch {:keys [on-success on-error close?] :as options}]
    (maybe-dispatch
      (let [buf-size (* 1024 16) ;; 16k is the undertow default if > 128M RAM is available
            buffer (byte-array buf-size)
            result (catch-and-notify ch on-error
                     (with-open [message message]
                       (loop []
                         (let [read-bytes (.read message buffer)]
                           (if (pos? read-bytes)
                             (let [latch (promise)]
                               (dispatch-message
                                 (if (< read-bytes buf-size)
                                   (Arrays/copyOfRange buffer 0 read-bytes)
                                   buffer)
                                 ch
                                 (assoc options
                                   :on-success #(deliver latch nil)
                                   :on-error   (partial deliver latch)
                                   :close?     false))
                               (if-let [err @latch]
                                 (notify ch on-error err)
                                 (recur))))))))]
        (when-not (= ::notified result)
          (notify ch on-success)))
      (when close?
        (close ch)))))

;; this has to be in a separate extend-protocol because we need to
;; extend Object first, and type looked up via Class/forName has to be
;; first in extend-protocol (see CLJ-1381)
(extend-protocol MessageDispatch
  (Class/forName "[B")
  (dispatch-message [message ch options]
    (wboss-send ch message options)))

(defn send!
  "Send a message to the channel, asynchronously.

  `message` can either be a `String`, `File`, `InputStream`, `ISeq`,
  `byte[]`, or map. If it is a `String`, it will be encoded to the
  character set of the response for HTTP streams, and as UTF-8 for
  WebSockets. `File`s and `InputStream`s will be sent as up to 16k
  chunks (each chunk being a `byte[]` message for WebSockets). Each
  item in an `ISeq` will pass through `send!`, and can be any of the
  valid message types.

  If `message` is a map, its :body entry must be one of the other
  valid message types, and its :status and :headers entries will be
  used to override the status or headers returned from the handler
  that called `as-channel` for HTTP streams. A map is *only* a valid
  message on the first send to an HTTP stream channel - an exception
  is thrown if it is passed on a subsequent send or passed to a
  WebSocket channel.

  The following options are supported [default]:

   * :close? - if `true`, the channel will be closed when the send completes.
     Setting this to `true` on the first send to an HTTP stream channel
     will cause it to behave like a standard HTTP response, and *not* chunk
     the response. [false]
   * :on-success - `(fn [] ...)` - called when the send attempt has completed
     successfully. If this callback throws an exception, it will be
     reported to the [[as-channel]] :on-error callback [nil]
   * :on-error - `(fn [throwable] ...)` - Called when an error occurs on the send.
     If the error requires the channel to be closed, the [[as-channel]] :on-close
     callback will also be invoked. If this callback throws an exception, it will be
     reported to the [[as-channel]] :on-error callback [`#(throw %)`]

   Returns nil if the channel is closed when the send is initiated, true
   otherwise. If the channel is already closed, :on-success won't be
   invoked."
  [^Channel ch message & options]
  (dispatch-message message ch
    (-> options
      u/kwargs-or-map->raw-map
      (o/validate-options send!))))

(o/set-valid-options! send! #{:close? :on-success :on-error})

(defn as-channel
  "Converts the current ring `request` in to an asynchronous channel.

  The type of channel created depends on the request - if the request
  is a Websocket upgrade request, a Websocket channel will be created.
  Otherwise, an HTTP stream channel is created. You interact with both
  channel types using the other functions in this namespace, and
  through the given `callbacks`.

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
  (see <http://tools.ietf.org/html/rfc6455#section-7.4>). Both will be nil
  for HTTP streams.

  If the channel is a Websocket, the following callback is also used:

  * :on-message - `(fn [ch message] ...)` - Called for each message
    from the client. `message` will be a `String` or `byte[]`

  When the ring handler is called during a WebSocket upgrade request,
  any headers returned in the response map are ignored, but any changes to
  the session are applied.

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
