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

(ns ^{:no-doc true} immutant.web.javax.servlet
    (:require [immutant.web.core :as core])
    (:import [javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse]))

(extend-type HttpServletRequest
  core/RingRequest
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
  (headers [request]            (core/headers->map request))
  (body [request]               (.getInputStream request))
  (ssl-client-cert [request]    (first (.getAttribute request "javax.servlet.request.X509Certificate"))))

(extend-protocol core/Headers
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
  (core/write-headers response headers)
  (core/write-body body (.getOutputStream response)))

(defn proxy-handler
  [handler]
  (proxy [HttpServlet] []
    (service [^HttpServletRequest request ^HttpServletResponse response]
      (let [ring-map (-> request
                       core/ring-request-map
                       (merge {:servlet          this
                               :servlet-request  request
                               :servlet-response response
                               :servlet-context  (.getServletContext ^HttpServlet this)}))]
      (if-let [result (handler ring-map)]
        (write-response response result)
        (throw (NullPointerException. "Ring handler returned nil")))))))
