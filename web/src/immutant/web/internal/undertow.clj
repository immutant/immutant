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
             [org.projectodd.wunderboss.web.async Channel$OnOpen Channel$OnClose
              Channel$OnError UndertowHttpChannel]
             [org.projectodd.wunderboss.web.async.websocket UndertowWebsocket
              UndertowWebsocketChannel
              WebsocketChannel WebsocketChannel$OnMessage WebsocketInitHandler]))

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
  ([exchange {:keys [timeout] :as options}]
   (condp instance? exchange
     HttpServerExchange    (let [hse ^HttpServerExchange exchange]
                             (when options
                               (set-cookie-config!
                                 (.getAttachment hse SessionConfig/ATTACHMENT_KEY)
                                 options))
                             (-> hse
                               Sessions/getOrCreateSession
                               (as-> session
                                   (if options
                                     (ring/set-session-expiry session timeout)
                                     session))))
     ;; we can't set options or create a session when handling a ws upgrade
     WebSocketHttpExchange (.getSession ^WebSocketHttpExchange exchange))))

(defn wrap-undertow-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the `io.undertow.server.session.Session` from the
  associated handler"
  [handler options]
  (let [fallback (delay (ring-session/wrap-session handler options))]
    (fn [request]
      (if-let [exchange (:server-exchange request)]
        (let [response (handler
                         (assoc request
                           ;; we assume the request map automatically derefs delays
                           :session (delay (ring/ring-session (get-or-create-session exchange options)))))]
          (when (contains? response :session)
            (if-let [data (:session response)]
              (when-let [session (get-or-create-session exchange)]
                (ring/set-ring-session! session data))
              (when [(instance? HttpServerExchange exchange)]
                ;; we can only invalidate sessions when handling an
                ;; http request
                (when-let [session (Sessions/getSession ^HttpServerExchange exchange)]
                  (.invalidate session exchange)))))
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
  (character-encoding [exchange] (if-let [type (ring/content-type exchange)]
                                   (Headers/extractTokenFromHeader type "charset")))
  (headers [exchange]            (-> exchange .getRequestHeaders hdr/headers->map))
  (body [exchange]               (.getInputStream exchange))
  (context [exchange]            (.getResolvedPath exchange))
  (path-info [exchange]          (let [v (.getRelativePath exchange)]
                                   (if (empty? v) "/" v)))
  (ssl-client-cert [_])

  ring/RingResponse
  (set-status [exchange status] (.setResponseCode exchange status))
  (header-map [exchange] (.getResponseHeaders exchange))
  (output-stream [exchange] (.getOutputStream exchange)))

(extend-type io.undertow.websockets.spi.WebSocketHttpExchange
  async/WebsocketHandshake
  (headers        [ex] (.getRequestHeaders ex))
  (parameters     [ex] (.getRequestParameters ex))
  (uri            [ex] (.getRequestURI ex))
  (query-string   [ex] (.getQueryString ex))
  (session        [ex] (-> ex .getSession ring/ring-session))
  (user-principal [ex] (.getUserPrincipal ex))
  (user-in-role?  [ex role] (.isUserInRole ex role)))

(extend-type java.util.Collections$UnmodifiableMap
  hdr/Headers
  (get-names [headers] (map str (.keySet headers)))
  (get-values [headers ^String key] (.get headers key))
  (get-value [headers ^String key] (first (hdr/get-values headers key)))
  (set-header [headers ^String k ^String v] (throw (Exception. "header map is read-only")))
  (add-header [headers ^String k ^String v] (throw (Exception. "header map is read-only"))))

(defn- ^HttpServerExchange reflect-exchange
  [^WebSocketHttpExchange wse]
  (-> io.undertow.websockets.spi.AsyncWebSocketHttpServerExchange
    (.getDeclaredField "exchange")
    (doto (.setAccessible true))
    (.get wse)))

(extend-type io.undertow.websockets.spi.WebSocketHttpExchange
  ring/RingRequest
  (server-port        [x] (-> x .getRequestURI URI. .getPort))
  (server-name        [x] (-> x .getRequestURI URI. .getHost))
  (remote-addr        [x] (ring/remote-addr (reflect-exchange x)))
  (uri                [x] (.getRequestURI x))
  (query-string       [x] (.getQueryString x))
  (scheme             [x] (-> x .getRequestScheme keyword))
  (request-method     [x] :get)
  (headers            [x] (-> x .getRequestHeaders hdr/headers->map))
  (context            [x] (ring/context (reflect-exchange x)))
  (path-info          [x] (ring/path-info (reflect-exchange x)))

  ;; no-ops
  (body               [x])
  (content-type       [x])
  (content-length     [x])
  (character-encoding [x])
  (ssl-client-cert    [x]))

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
  [_ {:keys [on-open on-error on-close on-message on-error]}]
  (UndertowWebsocketChannel.
    (reify Channel$OnOpen
      (handle [_ ch handshake]
        (.setHandshake ^WebsocketChannel ch handshake)
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

(defn ^:internal create-websocket-init-handler [handler-fn downstream-handler request-map-fn]
  (UndertowWebsocket/createHandler
    (reify WebsocketInitHandler
      (shouldConnect [_ exchange endpoint-wrapper]
        (boolean
          (let [body (:body (handler-fn (request-map-fn exchange
                                          [:websocket? true]
                                          [:server-exchange exchange]
                                          [:handler-type :undertow])))]
            (when (instance? WebsocketChannel body)
              (.setEndpoint endpoint-wrapper
                (.endpoint ^WebsocketChannel body))
              true)))))
    downstream-handler))
