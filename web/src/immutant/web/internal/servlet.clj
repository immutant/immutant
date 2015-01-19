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

(ns ^{:no-doc true} immutant.web.internal.servlet
    (:require [immutant.web.internal.ring    :as ring]
              [immutant.web.internal.headers :as hdr]
              [immutant.web.async            :as async])
    (:import [org.projectodd.wunderboss.web.async Channel$OnOpen Channel$OnClose ServletHttpChannel]
             [org.projectodd.wunderboss.web.async.websocket DelegatingJavaxEndpoint JavaxWebsocketChannel
              Util WebsocketChannel WebsocketChannel$OnMessage WebsocketChannel$OnError]
             [javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse HttpSession]
             [javax.servlet Servlet ServletConfig ServletContext]
             [javax.websocket Session Endpoint EndpointConfig MessageHandler$Whole CloseReason]
             [javax.websocket.server ServerContainer HandshakeRequest
              ServerEndpointConfig ServerEndpointConfig$Builder ServerEndpointConfig$Configurator]))

(defn- get-or-create-session
  ([servlet-request]
   (get-or-create-session servlet-request nil))
  ([servlet-request timeout]
   (condp instance? servlet-request
     HttpServletRequest (let [session (.getSession ^HttpServletRequest servlet-request)]
                          (if timeout
                            (ring/set-session-expiry session timeout)
                            session))
     ;; we can't set options or create a session when handling a ws upgrade
     HandshakeRequest   (.getHttpSession ^HandshakeRequest servlet-request))))

(defn wrap-servlet-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the possibly-replicated HttpSession from the
  associated servlet"
  [handler {:keys [timeout]}]
  (fn [request]
    (let [servlet-request (:servlet-request request)
          response (handler
                     (assoc request
                       ;; we assume the request map automatically derefs delays
                       :session (delay (-> servlet-request (get-or-create-session timeout) ring/ring-session))))]
      (when (contains? response :session)
        (if-let [data (:session response)]
          (ring/set-ring-session! (get-or-create-session servlet-request) data)
          (when (instance? HttpServletRequest servlet-request)
            ;; we can only invalidate sessions when handling an
            ;; http request
            (when-let [session (.getSession ^HttpServletRequest servlet-request false)]
              (.invalidate session)))))
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
  (path-info [request]          (.getPathInfo request))
  (ssl-client-cert [request]    (first (.getAttribute request "javax.servlet.request.X509Certificate")))
  hdr/Headers
  (get-names [request]      (enumeration-seq (.getHeaderNames request)))
  (get-values [request key] (enumeration-seq (.getHeaders request key))))

(extend-type HttpServletResponse
  ring/RingResponse
  (set-status [response status] (.setStatus response status))
  (header-map [response] response)
  (output-stream [response] (.getOutputStream response))
  hdr/Headers
  (get-value [response key] (.getHeader response key))
  (set-header [response key value] (.setHeader response key value))
  (add-header [response key value] (.addHeader response key value)))

(extend-type javax.websocket.server.HandshakeRequest
  async/WebsocketHandshake
  (headers        [hs] (.getHeaders hs))
  (parameters     [hs] (.getParameterMap hs))
  (uri            [hs] (str (.getRequestURI hs)))
  (query-string   [hs] (.getQueryString hs))
  (session        [hs] (-> hs .getHttpSession ring/ring-session))
  (user-principal [hs] (.getUserPrincipal hs))
  (user-in-role?  [hs role] (.isUserInRole hs role))

  ring/RingRequest
  (server-port        [hs] (-> hs .getRequestURI .getPort))
  (server-name        [hs] (-> hs .getRequestURI .getHost))
  (uri                [hs] (-> hs .getRequestURI .toString))
  (query-string       [hs] (.getQueryString hs))
  (scheme             [hs] (-> hs .getRequestURI .getScheme))
  (request-method     [hs] :get)
  (headers            [hs] (-> hs .getHeaders hdr/headers->map))
  ;; FIXME: should these be the same thing? probably not, inside the container
  (context            [hs] (-> hs .getRequestURI .getPath))
  (path-info          [hs] (-> hs .getRequestURI .getPath))

  ;; no-ops
  (remote-addr        [hs])
  (body               [hs])
  (content-type       [hs])
  (content-length     [hs])
  (character-encoding [hs])
  (ssl-client-cert    [hs]))

(extend-type javax.websocket.Session
  async/Channel
  (send!      [ch message] (.sendObject (.getAsyncRemote ch) message))
  (open? [ch] (.isOpen ch))
  (close      [ch] (.close ch)))

(defn ^Endpoint create-endpoint
  "Create a JSR-356 endpoint from one or more callback functions.

  The following callbacks are supported, where `channel` is an
  instance of `javax.websocket.Session`, extended to the
  [[immutant.web.websocket/Channel]] protocol, and `handshake` is an
  instance of `javax.websocket.server.HandshakeRequest`, extended to
  [[immutant.web.async/WebsocketHandshake]]:

    * :on-message `(fn [channel message])`
    * :on-open    `(fn [channel handshake])`
    * :on-close   `(fn [channel {:keys [code reason]}])`
    * :on-error   `(fn [channel throwable])`"
  ([key value & key-values]
     (create-endpoint (apply hash-map key value key-values)))
  ([{:keys [on-message on-open on-close on-error]}]
     (proxy [Endpoint] []
       (onOpen [^Session session ^EndpointConfig config]
         (when on-open (on-open session
                         ^HandshakeRequest (-> config
                                             .getUserProperties
                                             (get "HandshakeRequest"))))
         (when on-message
           (let [handler (reify MessageHandler$Whole
                           (onMessage [_ message] (on-message session message)))]
             (.addMessageHandler session (Util/createTextHandler handler))
             (.addMessageHandler session (Util/createBinaryHandler handler)))))
       (onClose [session ^CloseReason reason]
         (when on-close (on-close session
                          {:code (.. reason getCloseCode getCode)
                           :reason (.getReasonPhrase reason)})))
       (onError [session error]
         (when on-error (on-error session error))))))

(defn ^ServerContainer server-container [^ServletContext context]
  (.getAttribute context "javax.websocket.server.ServerContainer"))

(defn add-endpoint
  "Adds an endpoint to a container obtained from the servlet-context"
  ([^Endpoint endpoint ^ServletContext servlet-context]
     (add-endpoint endpoint servlet-context {}))
  ([^Endpoint endpoint ^ServletContext servlet-context {:keys [path handshake] :or {path "/"}}]
     (.addEndpoint (server-container servlet-context)
       (.. ServerEndpointConfig$Builder
         (create (class endpoint) path)
         (configurator (proxy [ServerEndpointConfig$Configurator] []
                         (getEndpointInstance [c] endpoint)
                         (modifyHandshake [^ServerEndpointConfig config request response]
                           (-> config
                               .getUserProperties
                               (.put "HandshakeRequest" request))
                           (when handshake
                             (handshake config request response)))))
         build))))

(defn handshake-ring-invoker [handler]
  (fn [^ServerEndpointConfig config request response]
    (let [body (:body (handler (ring/ring-request-map request
                                 [:handler-type :servlet]
                                 [:servlet-request request]
                                 [:websocket? true])))]
      (when (instance? WebsocketChannel body)
        (-> config
          .getUserProperties
          (.put "Endpoint" (.endpoint ^WebsocketChannel body)))))))

(defn ^Servlet create-servlet
  "Encapsulate a ring handler and an optional websocket endpoint
  within a servlet"
  ([handler]
     (create-servlet handler nil))
  ([handler endpoint]
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
           (let [context (.getServletContext config)
                 mapping (-> context (.getServletRegistration (.getServletName this)) .getMappings first)
                 path (apply str (take (- (count mapping) 2) mapping))
                 path (if (empty? path) "/" path)]
             (if endpoint
               (add-endpoint endpoint context {:path path})
               (add-endpoint (DelegatingJavaxEndpoint.) context
                 {:path path :handshake (handshake-ring-invoker handler)}))))))))

(defmethod async/initialize-stream :servlet
  [request {:keys [on-open on-close]}]
  (ServletHttpChannel.
    (:servlet-request request)
    (:servlet-response request)
    (when on-open
      (reify Channel$OnOpen
        (handle [_ ch _]
          (on-open ch))))
    (when on-close
      (reify Channel$OnClose
        (handle [_ ch code reason]
          (on-close ch {:code code
                        :reason reason}))))))

(defmethod async/initialize-websocket :servlet
  [_ {:keys [on-open on-close on-message on-error]}]
  (JavaxWebsocketChannel.
    (reify Channel$OnOpen
      (handle [_ ch config]
        (.setHandshake ^WebsocketChannel ch
          (-> ^ServerEndpointConfig config
            .getUserProperties
            (.get "HandshakeRequest")))
        (when on-open
          (on-open ch))))
    (reify Channel$OnClose
      (handle [_ ch code reason]
        (when on-close
          (on-close ch {:code code
                        :reason reason}))))
    (reify WebsocketChannel$OnMessage
      (handle [_ ch message]
        (when on-message
          (on-message ch message))))
    (reify WebsocketChannel$OnError
      (handle [_ ch error]
        (when on-error
          (on-error ch error))))))
