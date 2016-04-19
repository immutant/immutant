;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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
    (:require [clojure.string                :as str]
              [immutant.web.async            :as async]
              [immutant.web.internal.headers :as hdr]
              [immutant.web.internal.ring    :as ring]
              [ring.middleware.session       :as ring-session])
    (:import clojure.lang.ISeq
             io.undertow.io.Sender
             [io.undertow.server HttpHandler HttpServerExchange]
             [io.undertow.server.session Session SessionConfig SessionCookieConfig]
             [io.undertow.util HeaderMap Headers HttpString Sessions]
             [org.projectodd.wunderboss.web.async Channel
              Channel$OnOpen Channel$OnClose Channel$OnError]
             [org.projectodd.wunderboss.web.async.websocket WebsocketChannel
              WebsocketChannel$OnMessage]
             org.projectodd.wunderboss.web.undertow.async.UndertowHttpChannel
             [org.projectodd.wunderboss.web.undertow.async.websocket
              UndertowWebsocket UndertowWebsocketChannel WebsocketInitHandler]
             [java.io File InputStream]
             java.nio.charset.Charset))

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

;;
;; V1 comment:
;; We don't use .getRequestPath (HttpServerExchange) since it is
;; decoded. See IMMUTANT-195.
;;
;; ---
;; This function ported from V1.x, also see IMMUTANT-610
;; See also path-info' in immutant.web.internal.servlet. Un-DRYed
;; this to avoid the cost associated with reflection on the request
;; arg.
;;
(defn- path-info'
  "Takes a HttpServerExchange and returns a path-info string without
  any URL decoding performed upon it."
  [^HttpServerExchange request]
  (let [path-info (subs (.getRequestURI request)
                        (count (ring/context request)))]
    (if (str/blank? path-info)
      "/"
      path-info)))

(defn- force-dispatch? [body]
  (let [c (class body)]
    (some #{File InputStream ISeq} (conj (ancestors c) c))))

(extend-type HttpServerExchange
  ring/RingRequest
  (server-port [exchange]        (-> exchange .getDestinationAddress .getPort))
  (server-name [exchange]        (.getHostName exchange))
  (remote-addr [exchange]        (-> exchange .getSourceAddress .getAddress .getHostAddress))
  (uri [exchange]                (.getRequestURI exchange))
  (query-string [exchange]       (let [qs (.getQueryString exchange)]
                                   (if (= "" qs) nil qs)))
  (scheme [exchange]             (-> exchange .getRequestScheme keyword))
  (request-method [exchange]     (-> exchange .getRequestMethod .toString .toLowerCase keyword))
  (content-type [exchange]       (-> exchange .getRequestHeaders (.getFirst Headers/CONTENT_TYPE)))
  (content-length [exchange]     (.getRequestContentLength exchange))
  (character-encoding [exchange] (.getRequestCharset exchange))
  (headers [exchange]            (-> exchange .getRequestHeaders hdr/headers->map))
  (body [exchange]               (when (.isBlocking exchange) (.getInputStream exchange)))
  (context [exchange]            (.getResolvedPath exchange))
  (path-info [exchange]          (path-info' exchange))
  (ssl-client-cert [_])

  ring/RingResponse
  (set-status [exchange status]       (.setResponseCode exchange status))
  (header-map [exchange]              (.getResponseHeaders exchange))
  (resp-character-encoding [exchange] (or (.getResponseCharset exchange)
                                        hdr/default-encoding))
  (write-sync-response
    [exchange status headers body]
    (let [action
          (fn [out]
            (when status (ring/set-status exchange status))
            (hdr/set-headers (ring/header-map exchange) headers)
            (ring/write-body body out exchange))]
      (if (.isInIoThread exchange)
        (if (force-dispatch? body)
          ;; dispatch to the XNIO worker pool to free up the IO thread
          (.dispatch exchange (fn []
                                (.startBlocking exchange)
                                (action (.getOutputStream exchange))
                                (.endExchange exchange)))
          ;; use the async sender for speed on the IO thread
          (action (.getResponseSender exchange)))
        ;; .startBlocking has already been called, and
        ;; the exchange will end automatically when
        ;; the handler returns since we were directly dispatched
        (action (.getOutputStream exchange))))))

(defmethod ring/write-body [String Sender]
  [^String body ^Sender sender response]
  (.send sender body (Charset/forName (ring/resp-character-encoding response))))

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

(defn ^:internal ^HttpHandler create-http-handler [handler]
  (UndertowWebsocket/createHandler
    (reify WebsocketInitHandler
      (shouldConnect [_ exchange endpoint-wrapper]
        (boolean
          (let [{:keys [body headers] :as r} (handler (ring/ring-request-map exchange
                                                        [:websocket? true]
                                                        [:server-exchange exchange]
                                                        [:handler-type :undertow]))]
            (hdr/set-headers (.getResponseHeaders exchange) headers)
            (when (instance? WebsocketChannel body)
              (.setEndpoint endpoint-wrapper
                (.endpoint ^WebsocketChannel body))
              true)))))
    (reify HttpHandler
      (^void handleRequest [this ^HttpServerExchange exchange]
        (when-not (.isInIoThread exchange)
          (.startBlocking exchange))
        (let [ring-map (ring/ring-request-map exchange
                                     [:server-exchange exchange]
                                     [:handler-type :undertow])]
          (if-let [response (handler ring-map)]
            (ring/handle-write-error ring-map exchange response
              #(ring/write-response exchange response))
            (throw (NullPointerException. "Ring handler returned nil"))))))))
