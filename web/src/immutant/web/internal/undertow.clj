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
    (:require [immutant.web.internal.ring :as i]
              [ring.middleware.session :as ring])
    (:import [io.undertow.server HttpHandler HttpServerExchange]
             [io.undertow.server.session Session SessionConfig SessionCookieConfig]
             [io.undertow.util HeaderMap Headers HttpString Sessions]
             [io.undertow.websockets.spi WebSocketHttpExchange]))

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

(defn wrap-undertow-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the `io.undertow.server.session.Session` from the
  associated handler"
  [handler {:keys [timeout] :as options}]
  (let [expirer (i/session-expirer timeout)
        fallback (delay (ring/wrap-session handler options))]
    (fn [request]
      (if-let [hse ^HttpServerExchange (:server-exchange request)]
        (let [data (delay
                     (set-cookie-config! (.getAttachment hse SessionConfig/ATTACHMENT_KEY) options)
                     (-> hse Sessions/getOrCreateSession expirer i/ring-session))
              ;; we assume the request map automatically derefs delays
              response (handler (assoc request :session data))]
          (when (contains? response :session)
            (if-let [data (:session response)]
              (i/set-ring-session! (Sessions/getOrCreateSession hse) data)
              (when-let [session (Sessions/getSession hse)]
                (.invalidate session hse))))
          response)
        (@fallback request)))))

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
  i/Session
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value))
  (get-expiry [session]
    (.getMaxInactiveInterval session))
  (set-expiry [session timeout]
    (.setMaxInactiveInterval session timeout)))

(extend-type HeaderMap
  i/Headers
  (get-names [headers] (map str (.getHeaderNames headers)))
  (get-values [headers ^String key] (.get headers key))
  (get-value [headers ^String key] (.getFirst headers key))
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
  (context [exchange]            (.getResolvedPath exchange))
  (path-info [exchange]          (let [v (.getRelativePath exchange)]
                                   (if (empty? v) "/" v)))
  (ssl-client-cert [_])

  i/RingResponse
  (set-status [exchange status] (.setResponseCode exchange status))
  (header-map [exchange] (.getResponseHeaders exchange))
  (output-stream [exchange] (.getOutputStream exchange)))

(defn create-http-handler [handler]
  (reify HttpHandler
    (^void handleRequest [this ^HttpServerExchange exchange]
      (.startBlocking exchange)
      (if-let [response (handler (i/ring-request-map exchange [:server-exchange exchange]))]
        (i/write-response exchange response)
        (throw (NullPointerException. "Ring handler returned nil"))))))
