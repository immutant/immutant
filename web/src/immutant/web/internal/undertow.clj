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

(ns ^{:no-doc true} immutant.web.internal.undertow
    (:require [immutant.web.internal.ring :as i])
    (:import [io.undertow.server HttpHandler HttpServerExchange]
             io.undertow.server.session.Session
             [io.undertow.util HeaderMap Headers HttpString Sessions]
             [io.undertow.websockets.spi WebSocketHttpExchange]))

(defn wrap-undertow-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the `io.undertow.server.session.Session` from the
  associated handler"
  [handler]
  (fn [request]
    (let [^HttpServerExchange hse (:server-exchange request)
          data (delay (-> hse Sessions/getOrCreateSession i/ring-session))
          ;; we assume the request map automatically derefs delays
          response (handler (assoc request :session data))]
      (when (contains? response :session)
        (if-let [data (:session response)]
          (i/set-ring-session! (Sessions/getOrCreateSession hse) data)
          (when-let [session (Sessions/getSession hse)]
            (.invalidate session hse))))
      response)))

(defn ring-session
  "Temporarily use reflection until getSession returns something
  useful; see UNDERTOW-294"
  [^WebSocketHttpExchange handshake]
  (-> io.undertow.websockets.spi.AsyncWebSocketHttpServerExchange
    (.getDeclaredField "exchange")
    (doto (.setAccessible true))
    (.get handshake)
    Sessions/getSession
    i/ring-session))

(extend-type Session
  i/SessionAttributes
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value)))

(extend-type HeaderMap
  i/Headers
  (get-names [headers] (map str (.getHeaderNames headers)))
  (get-values [headers ^String key] (.get headers key))
  (set-header [headers ^String k ^String v] (.put headers (HttpString. k) v))
  (add-header [headers ^String k ^String v] (.add headers (HttpString. k) v)))

(extend-type HttpServerExchange
  i/RingRequest
  (server-port [exchange]        (-> exchange .getDestinationAddress .getPort))
  (server-name [exchange]        (.getHostName exchange))
  (remote-addr [exchange]        (-> exchange .getSourceAddress .getAddress .getHostAddress))
  (uri [exchange]                (.getRequestURI exchange))
  (query-string [exchange]       (.getQueryString exchange))
  (scheme [exchange]             (-> exchange .getRequestScheme keyword))
  (request-method [exchange]     (-> exchange .getRequestMethod .toString .toLowerCase keyword))
  (content-type [exchange]       (-> exchange .getRequestHeaders (.getFirst Headers/CONTENT_TYPE)))
  (content-length [exchange]     (.getRequestContentLength exchange))
  (character-encoding [exchange] (if-let [type (i/content-type exchange)]
                                   (Headers/extractTokenFromHeader type "charset")))
  (headers [exchange]            (-> exchange .getRequestHeaders i/headers->map))
  (body [exchange]               (.getInputStream exchange))
  (ssl-client-cert [_]))

(defn- write-response [^HttpServerExchange exchange {:keys [status headers body]}]
  (when status
    (.setResponseCode exchange status))
  (i/write-headers (.getResponseHeaders exchange) headers)
  (i/write-body body (.getOutputStream exchange)))

(defn handle-request [f ^HttpServerExchange exchange]
  (.startBlocking exchange)
  (try
    (if-let [response (f (i/ring-request-map exchange [:server-exchange exchange]))]
      (write-response exchange response)
      (throw (NullPointerException. "Ring handler returned nil")))
    (finally
      (.endExchange exchange))))

(defn create-http-handler [handler]
  (reify HttpHandler
    (^void handleRequest [this ^HttpServerExchange exchange]
      (if (.isInIoThread exchange)
        (.dispatch exchange this)
        (handle-request handler exchange)))))
