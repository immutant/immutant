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

(ns ^{:no-doc true} immutant.web.internal.servlet
    (:require [immutant.web.internal.ring :as i])
    (:import [org.projectodd.wunderboss.websocket Util]
             [javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse HttpSession]
             [javax.servlet Servlet ServletConfig ServletContext]
             [javax.websocket Session Endpoint EndpointConfig MessageHandler$Whole]
             [javax.websocket.server ServerContainer HandshakeRequest ServerEndpointConfig$Builder ServerEndpointConfig$Configurator]))

(defn wrap-servlet-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the possibly-replicated HttpSession from the
  associated servlet"
  [handler timeout]
  (let [expirer (i/session-expirer timeout)]
    (fn [request]
      (let [^HttpServletRequest hsr (:servlet-request request)
            data (delay (-> hsr .getSession expirer i/ring-session))
            ;; we assume the request map automatically derefs delays
            response (handler (assoc request :session data))]
        (when (contains? response :session)
          (if-let [data (:session response)]
            (i/set-ring-session! (.getSession hsr) data)
            (when-let [session (.getSession hsr false)]
              (.invalidate session))))
        response))))

(extend-type HttpSession
  i/SessionAttributes
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value)))

(extend-type HttpServletRequest
  i/RingRequest
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
  (headers [request]            (i/headers->map request))
  (body [request]               (.getInputStream request))
  (context [request]            (str (.getContextPath request) (.getServletPath request)))
  (path-info [request]          (.getPathInfo request))
  (ssl-client-cert [request]    (first (.getAttribute request "javax.servlet.request.X509Certificate")))
  i/Headers
  (get-names [request]      (enumeration-seq (.getHeaderNames request)))
  (get-values [request key] (enumeration-seq (.getHeaders request key))))

(extend-type HttpServletResponse
  i/RingResponse
  (set-status [response status] (.setStatus response status))
  (header-map [response] response)
  (output-stream [response] (.getOutputStream response))
  i/Headers
  (get-value [response key] (.getHeader response key))
  (set-header [response key value] (.setHeader response key value))
  (add-header [response key value] (.addHeader response key value)))

(defn ^Endpoint create-endpoint
  "Create a JSR-356 endpoint from one or more callback functions.

  The following callbacks are supported, where `channel` is an
  instance of `javax.websocket.Session`, extended to the
  [[immutant.web.websocket/Channel]] protocol, and `handshake` is an
  instance of `javax.websocket.server.HandshakeRequest`, extended to
  [[immutant.web.websocket/Handshake]]:

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
       (onClose [session reason]
         (when on-close (on-close session
                          {:code (.. reason getCloseCode getCode)
                           :reason (.getReasonPhrase reason)})))
       (onError [session error]
         (when on-error (on-error session error))))))

(defn add-endpoint
  "Adds an endpoint to a container obtained from the servlet-context"
  ([^Endpoint endpoint ^ServletContext servlet-context]
     (add-endpoint endpoint servlet-context {}))
  ([^Endpoint endpoint ^ServletContext servlet-context {:keys [path handshake] :or {path "/"}}]
     (let [^ServerContainer container (.getAttribute servlet-context "javax.websocket.server.ServerContainer")
           config (.. ServerEndpointConfig$Builder
                    (create (class endpoint) path)
                    (configurator (proxy [ServerEndpointConfig$Configurator] []
                                    (getEndpointInstance [c] endpoint)
                                    (modifyHandshake [config request response]
                                      (if handshake
                                        (handshake config request response)
                                        (-> config
                                          .getUserProperties
                                          (.put "HandshakeRequest" request))))))
                    build)]
       (.addEndpoint container config))))

(defn ^Servlet create-servlet
  "Encapsulate a ring handler and an optional websocket endpoint
  within a servlet"
  ([handler]
     (create-servlet handler nil))
  ([handler endpoint]
     (proxy [HttpServlet] []
       (service [^HttpServletRequest request ^HttpServletResponse response]
         (let [ring-map (-> request
                          (i/ring-request-map
                            [:servlet          this]
                            [:servlet-request  request]
                            [:servlet-response response]
                            [:servlet-context  (delay (.getServletContext ^HttpServlet this))]))]
           (if-let [result (if handler (handler ring-map) {:status 404})]
             (i/write-response response result)
             (throw (NullPointerException. "Ring handler returned nil")))))
       (init [^ServletConfig config]
         (proxy-super init config)
         (if endpoint
           (let [context (.getServletContext config)
                 mapping (-> context (.getServletRegistration (.getServletName this)) .getMappings first)
                 path (apply str (take (- (count mapping) 2) mapping))]
             (add-endpoint endpoint context {:path (if (empty? path) "/" path)})))))))
