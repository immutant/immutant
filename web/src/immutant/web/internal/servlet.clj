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

(ns ^{:no-doc true} immutant.web.internal.servlet
  (:require [clojure.string :as str]
            [immutant.web.internal.ring :as ring]
            [immutant.web.internal.headers :as hdr]
            [immutant.internal.util :refer [try-resolve warn]]
            [immutant.util :refer [in-eap?]]
            [immutant.web.async :as async])

  (:import [org.projectodd.wunderboss.web.async Channel
                                                Channel$OnOpen Channel$OnClose Channel$OnError
                                                ServletHttpChannel]
           [org.projectodd.wunderboss.web.async.websocket DelegatingJavaxEndpoint
                                                          JavaxWebsocketChannel WebSocketHelpyHelpertonFilter
                                                          WebsocketChannel WebsocketChannel$OnMessage]
           [javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse HttpSession]
           [javax.servlet Servlet ServletConfig ServletContext]
           [javax.websocket HandshakeResponse]
           [javax.websocket.server ServerContainer ServerEndpointConfig$Builder ServerEndpointConfig$Configurator]))

(defn- get-or-create-session
  ([servlet-request]
   (get-or-create-session servlet-request nil))
  ([^HttpServletRequest servlet-request timeout]
   (let [session (.getSession servlet-request)]
     (if timeout
       (ring/set-session-expiry session timeout)
       session))))

(defn wrap-servlet-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the possibly-replicated HttpSession from the
  associated servlet"
  [handler {:keys [timeout]}]
  (fn [request]
    (let [^HttpServletRequest servlet-request (:servlet-request request)
          response (handler
                     (assoc request
                       ;; we assume the request map automatically derefs delays
                       :session (delay (-> servlet-request (get-or-create-session timeout) ring/ring-session))))]
      (when (contains? response :session)
        (if-let [data (:session response)]
          (ring/set-ring-session! (get-or-create-session servlet-request) data)
          (when-let [session (.getSession servlet-request false)]
            (.invalidate session))))
      response)))

(extend-type HttpSession
  ring/Session
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value))
  (get-expiry [session]
    (.getMaxInactiveInterval session))
  (set-expiry [session timeout]
    (.setMaxInactiveInterval session timeout)))

;;
;; V1 comment:
;; We don't use .getPathInfo (HttpServletRequest) since it is
;; decoded. See IMMUTANT-195.
;;
;; ---
;; This function ported from V1.x, also see IMMUTANT-610
;; See also path-info' in immutant.web.internal.undertow. We un-DRYed
;; this to avoid the cost associated with reflection on the request
;; arg.
;;
(defn- path-info'
  "Takes a HttpServletRequest and returns a path-info string without
  any URL decoding performed upon it."
  [^HttpServletRequest request]
  (let [path-info (subs (.getRequestURI request)
                        (count (ring/context request)))]
    (if (str/blank? path-info)
      "/"
      path-info)))

(extend-type HttpServletRequest
  ring/RingRequest
  (server-port [request]        (.getServerPort request))
  (server-name [request]        (.getServerName request))
  (remote-addr [request]        (.getRemoteAddr request))
  (uri [request]                (.getRequestURI request))
  (query-string [request]       (.getQueryString request))
  (scheme [request]             (-> request .getScheme keyword))
  (request-method [request]     (-> request .getMethod .toLowerCase keyword))
  (content-type [request]       (.getContentType request))
  (content-length [request]     (.getContentLength request))
  (character-encoding [request] (.getCharacterEncoding request))
  (headers [request]            (hdr/headers->map request))
  (body [request]               (.getInputStream request))
  (context [request]            (str (.getContextPath request) (.getServletPath request)))
  (path-info [request]          (path-info' request))
  (ssl-client-cert [request]    (first (.getAttribute request "javax.servlet.request.X509Certificate")))
  hdr/Headers
  (get-names [request]      (enumeration-seq (.getHeaderNames request)))
  (get-values [request key] (enumeration-seq (.getHeaders request key))))

(extend-type HttpServletResponse
  ring/RingResponse
  (set-status [response status]       (.setStatus response status))
  (header-map [response]              response)
  (output [response]                  (.getOutputStream response))
  (resp-character-encoding [response] (or (.getCharacterEncoding response)
                                        hdr/default-encoding))
  hdr/Headers
  (get-value [response ^String key]        (.getHeader response key))
  (set-header [response ^String key value] (.setHeader response key value))
  (add-header [response key value]         (.addHeader response key value)))

(defn ^ServerContainer server-container [^ServletContext context]
  (.getAttribute context "javax.websocket.server.ServerContainer"))

(extend-type HandshakeResponse
  hdr/Headers
  (set-header [response ^String key value] (-> response .getHeaders (.put key [value])))
  (add-header [response ^String key value]
    (let [headers (.getHeaders response)
          curr (.get headers key)]
      (.put headers key
        (if curr
          (conj (vec curr) value)
          [value])))))

(defn add-endpoint-with-handler
  [^Servlet servlet ^ServletConfig config handler]
  (let [servlet-context (.getServletContext config)
        mapping (-> servlet-context
                  (.getServletRegistration (-> servlet .getServletConfig .getServletName))
                  .getMappings
                  first)
        path (apply str (take (- (count mapping) 2) mapping))
        path (if (empty? path) "/" path)]
    (if-let [container (server-container servlet-context)]
      (.addEndpoint container
        (.. ServerEndpointConfig$Builder
          (create DelegatingJavaxEndpoint path)
          (configurator (proxy [ServerEndpointConfig$Configurator] []
                          (getEndpointInstance [_] (DelegatingJavaxEndpoint.))
                          (modifyHandshake [_ _ ^HandshakeResponse response]
                            (let [request (.get WebSocketHelpyHelpertonFilter/requestTL)
                                  {:keys [body headers]} (handler (ring/ring-request-map request
                                                                    [:handler-type :servlet]
                                                                    [:servlet-request request]
                                                                    [:websocket? true]))]
                              (hdr/set-headers response headers)
                              (when (instance? WebsocketChannel body)
                                (DelegatingJavaxEndpoint/setCurrentDelegate
                                  (.endpoint ^WebsocketChannel body)))))))
          build))
      (if (in-eap?)
        (warn "Websockets unavailable; see the Immutant EAP guide for details.")
        (warn "Websockets unavailable; please check your configuration.")))))

(defn add-endpoint
  "Adds a javax.websocket.Endpoint for the given `servlet` and `servlet-config`.

  `ws-callbacks` is a map of callbacks for handling the WebSocket, and are
  the same as the callbacks provided to [[immutant.web.async/as-channel]]."
  [^Servlet servlet ^ServletConfig servlet-config ws-callbacks]
  (add-endpoint-with-handler servlet servlet-config
    (fn [req]
      (async/as-channel req ws-callbacks))))

(defn ^Servlet create-servlet
  "Encapsulate a ring handler within a servlet"
  [handler]
  (proxy [HttpServlet] []
    (service [^HttpServletRequest request ^HttpServletResponse response]
      (let [ring-map (-> request
                       (ring/ring-request-map
                         [:handler-type     :servlet]
                         [:servlet          this]
                         [:servlet-request  request]
                         [:servlet-response response]
                         [:servlet-context  (delay (.getServletContext ^HttpServlet this))]))]
        (if-let [result (if handler (handler ring-map) {:status 404})]
          (ring/write-response response result)
          (throw (NullPointerException. "Ring handler returned nil")))))
    (init [^ServletConfig config]
      (let [^HttpServlet this this]
        (proxy-super init config)
        (add-endpoint-with-handler this config handler)))))

(defn async-streaming-supported? []
  (when-let [f (try-resolve 'immutant.wildfly/async-streaming-supported?)]
    (f)))

(defmethod async/initialize-stream :servlet
  [request {:keys [on-open on-error on-close]}]
  (ServletHttpChannel.
    (:servlet-request request)
    (:servlet-response request)
    (reify Channel$OnOpen
      (handle [_ ch _]
        (.attach ^Channel ch :originating-request request)
        (when on-open
          (on-open ch))))
    (when on-error
      (reify Channel$OnError
        (handle [_ ch error]
          (on-error ch error))))
    (when on-close
      (reify Channel$OnClose
        (handle [_ ch code reason]
          (on-close ch {:code code
                        :reason reason}))))
    (boolean (async-streaming-supported?))))

(defmethod async/initialize-websocket :servlet
  [request {:keys [on-open on-error on-close on-message on-error]}]
  (JavaxWebsocketChannel.
    (reify Channel$OnOpen
      (handle [_ ch config]
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

(defn websocket-servlet-filter-map []
  {"ws-helper" (WebSocketHelpyHelpertonFilter.)})
