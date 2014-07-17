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

(ns ^{:no-doc true} immutant.web.undertow
    (:require [immutant.web.core :as core])
    (:import [io.undertow.server HttpHandler HttpServerExchange]
             [io.undertow.util HeaderMap Headers HttpString]))

(extend-type HeaderMap
  core/Headers
  (get-names [headers] (map str (.getHeaderNames headers)))
  (get-values [headers ^String key] (.get headers key))
  (set-header [headers ^String k ^String v] (.put headers (HttpString. k) v))
  (add-header [headers ^String k ^String v] (.add headers (HttpString. k) v)))

(extend-type HttpServerExchange
  core/RingRequest
  (server-port [exchange]        (-> exchange .getDestinationAddress .getPort))
  (server-name [exchange]        (.getHostName exchange))
  (remote-addr [exchange]        (-> exchange .getSourceAddress .getAddress .getHostAddress))
  (uri [exchange]                (.getRequestURI exchange))
  (query-string [exchange]       (.getQueryString exchange))
  (scheme [exchange]             (-> exchange .getRequestScheme keyword))
  (request-method [exchange]     (-> exchange .getRequestMethod .toString .toLowerCase keyword))
  (content-type [exchange]       (-> exchange .getRequestHeaders (.getFirst Headers/CONTENT_TYPE)))
  (content-length [exchange]     (.getRequestContentLength exchange))
  (character-encoding [exchange] (if-let [type (core/content-type exchange)]
                                   (Headers/extractTokenFromHeader type "charset")))
  (headers [exchange]            (-> exchange .getRequestHeaders core/headers->map))
  (body [exchange]               (.getInputStream exchange))
  (ssl-client-cert [_]))

(defn- write-response [^HttpServerExchange exchange {:keys [status headers body]}]
  (when status
    (.setResponseCode exchange status))
  (core/write-headers (.getResponseHeaders exchange) headers)
  (core/write-body body (.getOutputStream exchange)))

(defn handle-request [f ^HttpServerExchange exchange]
  (.startBlocking exchange)
  (try
    (if-let [response (f (core/ring-request-map exchange))]
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

