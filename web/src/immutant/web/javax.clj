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
  (:require [ring.util.servlet :as ring])
  (:import [org.projectodd.wunderboss.websocket Util]
           [javax.servlet.http HttpServlet HttpServletRequest]
           [javax.websocket Endpoint MessageHandler$Whole]
           [javax.websocket.server ServerEndpointConfig$Builder ServerEndpointConfig$Configurator]))

(defn create-servlet
  "Encapsulate a ring handler within a servlet's service method"
  [handler]
  (ring/servlet handler))

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

(defn create-endpoint-servlet
  "Create a servlet for a JSR-356 endpoint"
  [endpoint {:keys [fallback path] :or {path "/"}}]
  (proxy [HttpServlet] []
    (init [servlet-config]
      (proxy-super init servlet-config)
      (let [context (.getServletContext servlet-config)
            container (.getAttribute context "javax.websocket.server.ServerContainer")
            config (.. ServerEndpointConfig$Builder
                     (create (class endpoint) path)
                     (configurator (proxy [ServerEndpointConfig$Configurator] []
                                     (getEndpointInstance [c] endpoint)))
                     build)]
        (.addEndpoint container config)))
    (service [request response]
      (if-let [fallback (and fallback (ring/make-service-method fallback))]
        (fallback this request response)
        (proxy-super service request response)))))

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
