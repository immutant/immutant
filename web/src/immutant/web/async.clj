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
  (:import [org.projectodd.wunderboss.websocket UndertowWebsocket WebsocketInitHandler]
           [org.projectodd.wunderboss.web.async HttpChannel WebsocketChannel]))

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

     Sending is asynchronous for WebSockets, but blocking for
     HTTP channels.

     Returns nil if the channel is closed, true otherwise."))

(extend-type org.projectodd.wunderboss.web.async.Channel
  Channel
  (open? [ch] (.isOpen ch))
  (close [ch] (.close ch HttpChannel/COMPLETE))
  (send!
    ([ch message]
        ;; TODO: throw if message isn't String or bytes[]? support codecs?
       (.send ch message))
    ([ch message close?]
       (.send ch message close?))))

(defprotocol Handshake
  "Reflects the state of the initial websocket upgrade request"
  (headers [hs] "Return request headers")
  (parameters [hs] "Return map of params from request")
  (uri [hs] "Return full request URI")
  (query-string [hs] "Return query portion of URI")
  (session [hs] "Return the user's session data, if any")
  (user-principal [hs] "Return authorized `java.security.Principal`")
  (user-in-role? [hs role] "Is user in role identified by String?"))

(defn create-websocket-init-handler [handler-fn downstream-handler request-map-fn]
  (UndertowWebsocket/createHandler
    (reify WebsocketInitHandler
      (shouldConnect [_ exchange endpoint-wrapper]
        (boolean
          (let [body (:body (handler-fn (request-map-fn exchange
                                          [:websocket? true])))]
            (when (instance? WebsocketChannel body)
              (.setEndpoint endpoint-wrapper
                (.getEndpoint ^WebsocketChannel body))
              true)))))
    downstream-handler))

(defn streaming-body? [body]
  (instance? HttpChannel body))

(defn add-streaming-headers [headers]
  headers
  ;;  (assoc headers "Transfer-Encoding" "chunked" "baz" "ffs")
  )

(defn open-stream [^HttpChannel channel headers]
  (.open channel nil))

(defn handler-type [request]
  (if (:servlet request)
      :servlet
      :handler))

(defmulti initialize-stream
  (fn [request & _]
    (handler-type request)))

(defmulti initialize-websocket
  (fn [request & _]
    (handler-type request)))

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
               ;;(reset! a-stream stream)
               (.start (Thread.
                         (fn []
                           (dotimes [n 10]
                             (Thread/sleep 1000)
                             (println n)
                             (when (s/open? stream)
                               (println "open")
                               (s/send! stream (format "%s\n%s\n" n data) false)
                               (println "sent")))
                           (s/close stream)))))
             :on-close
             (fn [stream reason]
               (println "CLOSED" stream reason))})
        :headers {"foo" "bar"})))

  (web/run handler)
  )
