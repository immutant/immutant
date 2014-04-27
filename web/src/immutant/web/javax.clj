;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.web.javax
  (:require [ring.util.servlet :as ring])
  (:import [org.projectodd.wunderboss.web Util]
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
  [{:keys [fallback path] :or {path "/"} :as callbacks}]
  (proxy [HttpServlet] []
    (init [servlet-config]
      (proxy-super init servlet-config)
      (let [context (.getServletContext servlet-config)
            container (.getAttribute context "javax.websocket.server.ServerContainer")
            endpoint (create-endpoint callbacks)
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
