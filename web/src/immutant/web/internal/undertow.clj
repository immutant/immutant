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

(ns ^{:no-doc true} immutant.web.internal.undertow
    (:require [immutant.web.async            :as async]
              [immutant.web.internal.ring    :as ring]
              [immutant.web.internal.headers :as hdr]
              [ring.middleware.session       :as ring-session])
    (:import java.net.URI
             [io.undertow.server HttpHandler HttpServerExchange]
             [io.undertow.server.session Session SessionConfig SessionCookieConfig]
             [io.undertow.util HeaderMap Headers HttpString Sessions]
             [io.undertow.websockets.core CloseMessage WebSocketChannel]
             [io.undertow.websockets.spi WebSocketHttpExchange]
             [org.projectodd.wunderboss.web.async Channel
              Channel$OnOpen Channel$OnClose Channel$OnError]
             [org.projectodd.wunderboss.web.async.websocket WebsocketChannel
              WebsocketChannel$OnMessage]
             [org.projectodd.wunderboss.web.undertow.async
              UndertowHttpChannel]
             [org.projectodd.wunderboss.web.undertow.async.websocket
              UndertowWebsocket
              UndertowWebsocketChannel
              WebsocketInitHandler]))

(def ^{:tag SessionCookieConfig :private true} set-cookie-config!
  (memoize
    (fn [^SessionCookieConfig config
        {:keys [cookie-name]
         {:keys [path domain max-age secure http-only]} :cookie-attrs}]
      (cond-> config
        cookie-name (.setCookieName cookie-name)
        path        (.setPath path)
        domain      (.setDomain domain)
        max-age     (.setMaxAge max-age)
        secure      (.setSecure secure)
        http-only   (.setHttpOnly http-only)))))

(defn- get-or-create-session
  ([exchange]
   (get-or-create-session exchange nil))
  ([^HttpServerExchange exchange {:keys [timeout] :as options}]
   (when options
     (set-cookie-config!
       (.getAttachment exchange SessionConfig/ATTACHMENT_KEY)
       options))
   (-> exchange
     Sessions/getOrCreateSession
     (as-> session
         (if options
           (ring/set-session-expiry session timeout)
           session)))))

(defn wrap-undertow-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the `io.undertow.server.session.Session` from the
  associated handler"
  [handler options]
  (let [fallback (delay (ring-session/wrap-session handler options))]
    (fn [request]
      (if-let [^HttpServerExchange exchange (:server-exchange request)]
        (let [response (handler
                         (assoc request
                           ;; we assume the request map automatically derefs delays
                           :session (delay (ring/ring-session (get-or-create-session exchange options)))))]
          (when (contains? response :session)
            (if-let [data (:session response)]
              (when-let [session (get-or-create-session exchange)]
                (ring/set-ring-session! session data))
              (when-let [session (Sessions/getSession exchange)]
                (.invalidate session exchange))))
          response)
        (@fallback request)))))

(extend-type Session
  ring/Session
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value))
  (get-expiry [session]
    (.getMaxInactiveInterval session))
  (set-expiry [session timeout]
    (.setMaxInactiveInterval session timeout)))

(extend-type HeaderMap
  hdr/Headers
  (get-names [headers] (map str (.getHeaderNames headers)))
  (get-values [headers ^String key] (.get headers key))
  (get-value [headers ^String key] (.getFirst headers key))
  (set-header [headers ^String k ^String v] (.put headers (HttpString. k) v))
  (add-header [headers ^String k ^String v] (.add headers (HttpString. k) v)))

(extend-type HttpServerExchange
  ring/RingRequest
  (server-port [exchange]        (-> exchange .getDestinationAddress .getPort))
  (server-name [exchange]        (.getHostName exchange))
  (remote-addr [exchange]        (-> exchange .getSourceAddress .getAddress .getHostAddress))
  (uri [exchange]                (.getRequestURI exchange))
  (query-string [exchange]       (.getQueryString exchange))
  (scheme [exchange]             (-> exchange .getRequestScheme keyword))
  (request-method [exchange]     (-> exchange .getRequestMethod .toString .toLowerCase keyword))
  (content-type [exchange]       (-> exchange .getRequestHeaders (.getFirst Headers/CONTENT_TYPE)))
  (content-length [exchange]     (.getRequestContentLength exchange))
  (character-encoding [exchange] (.getRequestCharset exchange))
  (headers [exchange]            (-> exchange .getRequestHeaders hdr/headers->map))
  (body [exchange]               (when (.isBlocking exchange) (.getInputStream exchange)))
  (context [exchange]            (.getResolvedPath exchange))
  (path-info [exchange]          (let [v (.getRelativePath exchange)]
                                   (if (empty? v) "/" v)))
  (ssl-client-cert [_])

  ring/RingResponse
  (set-status [exchange status]       (.setResponseCode exchange status))
  (header-map [exchange]              (.getResponseHeaders exchange))
  (output-stream [exchange]           (.getOutputStream exchange))
  (resp-character-encoding [exchange] (or (.getResponseCharset exchange)
                                        hdr/default-encoding)))

(defn create-http-handler [handler]
  (reify HttpHandler
    (^void handleRequest [this ^HttpServerExchange exchange]
      (.startBlocking exchange)
      (if-let [response (handler (ring/ring-request-map exchange
                                   [:server-exchange exchange]
                                   [:handler-type :undertow]))]
        (ring/write-response exchange response)
        (throw (NullPointerException. "Ring handler returned nil"))))))

(defmethod async/initialize-stream :undertow
  [request {:keys [on-open on-error on-close]}]
  (UndertowHttpChannel.
    (:server-exchange request)
    (when on-open
      (reify Channel$OnOpen
        (handle [_ ch _]
          (.attach ^Channel ch :originating-request request)
          (on-open ch))))
    (when on-error
      (reify Channel$OnError
        (handle [_ ch error]
          (on-error ch error))))
    (when on-close
      (reify Channel$OnClose
        (handle [_ ch code reason]
          (on-close ch {:code code :reason reason}))))))

(defmethod async/initialize-websocket :undertow
  [request {:keys [on-open on-error on-close on-message on-error]}]
  (UndertowWebsocketChannel.
    (reify Channel$OnOpen
      (handle [_ ch _]
        (.attach ^Channel ch :originating-request request)
        (when on-open
          (on-open ch))))
    (reify Channel$OnError
      (handle [_ ch error]
        (when on-error
          (on-error ch error))))
    (reify Channel$OnClose
      (handle [_ ch code reason]
        (when on-close
          (on-close ch {:code code
                        :reason reason}))))
    (reify WebsocketChannel$OnMessage
      (handle [_ ch message]
        (when on-message
          (on-message ch message))))))

(defn ^:internal create-websocket-init-handler [handler-fn request-map-fn]
  (let [http-exchange-tl (ThreadLocal.)
        downstream-handler (create-http-handler handler-fn)]
    (UndertowWebsocket/createHandler
      http-exchange-tl
      (reify WebsocketInitHandler
        (shouldConnect [_ exchange endpoint-wrapper]
          (let [http-exchange (.get http-exchange-tl)]
            (boolean
              (let [body (:body (handler-fn (request-map-fn http-exchange
                                              [:websocket? true]
                                              [:server-exchange http-exchange]
                                              [:handler-type :undertow])))]
                (when (instance? WebsocketChannel body)
                  (.setEndpoint endpoint-wrapper
                    (.endpoint ^WebsocketChannel body))
                  true))))))
      downstream-handler)))
