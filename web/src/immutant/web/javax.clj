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

(ns ^{:no-doc true} immutant.web.javax
  (:require [ring.util.servlet :as ring]
            [ring.middleware.session :refer (wrap-session)]
            [immutant.web.javax.session :refer (bind-http-session ->ServletStore)])
  (:import [org.projectodd.wunderboss.websocket Util]
           [javax.servlet.http HttpServlet HttpServletRequest]
           [javax.websocket Endpoint MessageHandler$Whole]
           [javax.websocket.server ServerEndpointConfig$Builder ServerEndpointConfig$Configurator]))

(defn wrap-session-servlet-store
  "Store the ring session data in the servlet's HttpSession; any
  options other than :store are passed on to ring's wrap-session"
  ([handler]
     (wrap-session-servlet-store handler {}))
  ([handler opts]
     (-> handler
       (wrap-session (merge opts {:store (->ServletStore)}))
       bind-http-session)))

(defn create-servlet
  "Encapsulate a ring handler within a servlet's service method,
  storing :session data in the associated HttpSession"
  [handler]
  (-> handler
    wrap-session-servlet-store
    ring/servlet))

(defn create-endpoint
  "Create a JSR-356 endpoint from a few functions"
  [{:keys [on-message on-open on-close on-error]}]
  (proxy [Endpoint] []
    (onOpen [session config]
      (when on-open (on-open session))
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
      (when on-error (on-error session error)))))

(defn configure-endpoint
  [endpoint servlet-config {:keys [path handshake] :or {path "/"}}]
  (let [context (.getServletContext servlet-config)
        container (.getAttribute context "javax.websocket.server.ServerContainer")
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
    (.addEndpoint container config)))

(defn attach-endpoint
  "Attach a JSR-356 endpoint to a servlet"
  [servlet endpoint args]
  (if-let [config (.getServletConfig servlet)]
    (do
      (configure-endpoint endpoint config args)
      servlet)
    (proxy [javax.servlet.Servlet] []
      (init [servlet-config]
        (.init servlet servlet-config)
        (configure-endpoint endpoint servlet-config args))
      (service [request response]
        (.service servlet request response))
      (destroy []
        (.destroy servlet))
      (getServletConfig []
        (.getServletConfig servlet))
      (getServletInfo []
        (.getServletInfo servlet)))))

(defn session
  "Returns the servlet session from the ring request"
  [request]
  (if-let [^HttpServletRequest hsr (:servlet-request request)]
    (.getSession hsr)))

(defn context
  "Returns the servlet context path from the ring request"
  [request]
  (if-let [^HttpServletRequest hsr (:servlet-request request)]
    (str (.getContextPath hsr) (.getServletPath hsr))))

(defn path-info
  "Returns the servlet path info from the ring request"
  [request]
  (if-let [^HttpServletRequest hsr (:servlet-request request)]
    (let [result (.substring (.getRequestURI hsr) (.length (context hsr)))]
      (if (.isEmpty result)
        "/"
        result))))
