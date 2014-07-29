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
    (:import [javax.servlet.http HttpSession HttpServlet HttpServletRequest HttpServletResponse]))

(def ring-session-key "ring-session-data")
(defn ring-session [^HttpSession session]
  (.getAttribute session ring-session-key))
(defn set-ring-session! [^HttpSession session, data]
  (.setAttribute session ring-session-key data))

(defn wrap-servlet-session
  "Ring middleware to insert :session key into request if not present,
  its value stored in the possibly-replicated HttpSession from the
  associated servlet"
  [handler]
  (fn [request]
    (if (contains? request :session)
      (handler request)
      (let [^HttpServletRequest hsr (:servlet-request request)
            data (delay (-> hsr .getSession ring-session))
            response (handler (i/->LazyMap (assoc request :session data)))]
        (if (contains? response :session)
          (if-let [data (:session response)]
            (set-ring-session! (.getSession hsr) data)
            (when-let [session (.getSession hsr false)]
              (.invalidate session))))
        response))))

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
  (ssl-client-cert [request]    (first (.getAttribute request "javax.servlet.request.X509Certificate"))))

(extend-protocol i/Headers
  HttpServletRequest
  (get-names [request]      (enumeration-seq (.getHeaderNames request)))
  (get-values [request key] (enumeration-seq (.getHeaders request key)))
  HttpServletResponse
  (set-header [response key value] (.setHeader response key value))
  (add-header [response key value] (.addHeader response key value)))

(defn write-response
  "Update the HttpServletResponse from the ring response map."
  [^HttpServletResponse response, {:keys [status headers body]}]
  (when status
    (.setStatus response status))
  (i/write-headers response headers)
  (i/write-body body (.getOutputStream response)))

(defn proxy-handler
  [handler]
  (proxy [HttpServlet] []
    (service [^HttpServletRequest request ^HttpServletResponse response]
      (let [ring-map (-> request
                       i/ring-request-map
                       (merge {:servlet          this
                               :servlet-request  request
                               :servlet-response response
                               :servlet-context  (.getServletContext ^HttpServlet this)}))]
      (if-let [result (handler ring-map)]
        (write-response response result)
        (throw (NullPointerException. "Ring handler returned nil")))))))
