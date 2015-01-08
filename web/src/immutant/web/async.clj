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

(ns immutant.web.async
  (:require [immutant.web.internal.headers :as hdr])
  (:import [java.io OutputStream IOException]
           java.net.URI
           java.util.concurrent.atomic.AtomicBoolean
           [org.projectodd.wunderboss.websocket UndertowWebsocket Endpoint WebsocketInitHandler]))

(defprotocol Channel
  "Streaming channel interface"
  (open? [ch] "Is the channel open?")
  (close [ch]
    "Gracefully close the channel.

     This will trigger the on-close callback for the channel if one is
     registered.")
  (send! [ch message] [ch message close?]
    "Send a message to the channel.

     If close? is truthy, close the channel after writing. close?
     defaults to false for WebSockets, true otherwise.

     The data is sent asynchronously for WebSockets, but blocks for
     HTTP channels.

     Returns nil if the channel is closed, true otherwise."))

(defrecord StreamChannel [^OutputStream os on-close ^AtomicBoolean open-state encoding]
  Channel
  (close [this]
    (when (open? this)
      (.close os)
      (.set open-state false)
      (when on-close
        (on-close this nil))))
  (send! [this message]
    (send! this message true))
  (send! [this message close?]
    (when (open? this)
      (let [bytes (if (instance? String message)
                    (.getBytes message encoding)
                    message)]
        ;; TODO: throw if message isn't String or bytes[]? support codecs?
        (try
          (.write os bytes)
          (if close?
            (close this)
            (.flush os))
          true
          ;; TODO: should we only deal with "Broken pipe" IOE's here?
          ;; rethrow others?
          (catch IOException e
            (try
              (close this)
              ;; undertow throws when you close with unwritten data,
              ;; but the data can never be written - see UNDERTOW-368
              (catch IOException ignored))
            nil)))))
  (open? [_]
    (.get open-state)))

(defrecord StreamMarker [os callbacks])

(defprotocol Handshake
  "Reflects the state of the initial websocket upgrade request"
  (headers [hs] "Return request headers")
  (parameters [hs] "Return map of params from request")
  (uri [hs] "Return full request URI")
  (query-string [hs] "Return query portion of URI")
  (session [hs] "Return the user's session data, if any")
  (user-principal [hs] "Return authorized `java.security.Principal`")
  (user-in-role? [hs role] "Is user in role identified by String?"))

(defrecord WebsocketMarker
    [channel-promise endpoint])

(defn create-websocket-init-handler [handler-fn downstream-handler request-map-fn]
  (UndertowWebsocket/createConditionalUpgradeHandler
    (reify WebsocketInitHandler
      (shouldConnect [_ exchange endpoint-wrapper]
        (boolean
          (let [body (:body (handler-fn (request-map-fn exchange
                                          [:websocket? true])))]
            (when (instance? WebsocketMarker body)
              (.setEndpoint endpoint-wrapper (:endpoint body))
              true)))))
    downstream-handler))

(defn create-wboss-endpoint [chan-promise {:keys [on-message on-open on-close on-error]}]
  (reify Endpoint
    (onMessage [_ channel message]
      (when on-message
        (on-message channel message)))
    (onOpen [_ channel exchange]
      (when chan-promise
        (deliver chan-promise channel))
      (when on-open
        (on-open channel exchange)))
    (onClose [_ channel cm]
      (when on-close
        (on-close channel {:code (.getCode cm) :reason (.getReason cm)})))
    (onError [_ channel error]
      (when on-error
        (on-error channel error)))))

(defn initialize-websocket
  [request callbacks]
  (when-not (:websocket? request)
    (throw (IllegalStateException. "The request isn't a websocket upgrade.")))
  (let [chan-promise (promise)]
    (->WebsocketMarker chan-promise (create-wboss-endpoint chan-promise callbacks))))

(defn streaming-body? [body]
  (instance? StreamMarker body))

(defn add-streaming-headers [headers]
  headers
  ;;  (assoc headers "Transfer-Encoding" "chunked" "baz" "ffs")
  )

(defn open-stream [{:keys [os callbacks]} headers]
  (when-let [on-open (:on-open callbacks)]
    (on-open (->StreamChannel os
               (:on-close callbacks)
               (AtomicBoolean. true)
               (or (hdr/get-character-encoding headers)
                 hdr/default-encoding)))))

;; on-open on-close
(defn initialize-stream [request callbacks]
  (->StreamMarker
    (-> request
      :server-exchange
      (.setPersistent true)
      .dispatch
      .getOutputStream)
    callbacks))

(defn as-channel [request callbacks]
  (let [ch (if (:websocket? request)
             (initialize-websocket request callbacks)
             (initialize-stream request callbacks))]
    {:status 200
     :body ch}))

(comment
  (require '[immutant.web.async :as s]
    '[immutant.web :as web])

  (def a-stream (atom nil))

  (defn handler [req]
    (println "called")
    (let [data "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a diam lectus. Sed sit amet ipsum mauris. Maecenas congue ligula ac quam viverra nec consectetur ante hendrerit. Donec et mollis dolor. Praesent et diam eget libero egestas mattis sit amet vitae augue. Nam tincidunt congue enim, ut porta lorem lacinia consectetur. Donec ut libero sed arcu vehicula ultricies a non tortor. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean ut gravida lorem. Ut turpis felis, pulvinar a semper sed, adipiscing id dolor. Pellentesque auctor nisi id magna consequat sagittis. Curabitur dapibus enim sit amet elit pharetra tincidunt feugiat nisl imperdiet. Ut convallis libero in urna ultrices accumsan. Donec sed odio eros. Donec viverra mi quis quam pulvinar at malesuada arcu rhoncus. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. In rutrum accumsan ultricies. Mauris vitae nisi at sem facilisis semper ac in est."]
      (assoc
          (s/as-channel req
            {:on-open
             (fn [stream]
               (println "OPEN" stream)
               (reset! a-stream stream)
               (.start (Thread.
                         (fn []
                           (dotimes [n 10]
                             (Thread/sleep 1000)
                             (when (s/open? stream)
                               (s/send! stream (format "%s\n%s\n" n data))))
                           (s/close stream)))))
             :on-close
             (fn [stream]
               (println "CLOSED" stream))})
        :headers {"foo" "bar"})))

  (web/run handler)
  )
