;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc immutant.web.session.internal
  (:use [immutant.web.internal :only [current-servlet-request]])
  (:require [immutant.util :as util]
            [immutant.web.session :as session]))

(def ^:private cookie-encoder
  (util/try-resolve-any
   'ring.util.codec/form-encode  ;; ring >= 1.1.0
   'ring.util.codec/url-encode))

(defn ^:private cookie-matches-servlet-session-cookie?
  [^javax.servlet.http.HttpSession session ^String cookie]
  (.startsWith cookie
               (str (:cookie-name (session/session-cookie-attributes))
                    \=
                    (cookie-encoder (.getId session)))))

(defn ^:internal servlet-cookie-dedup-handler
  "Remove duplicate cookies from a response object.  Use this
   in an interceptor before the session interceptor running
   inside the ServletProxy, e.g.
   (defon-response dedup-session-cookies [response]
     (session-internal/servlet-cookie-dedup-handler response)"
  [response]
  (let [session (.getSession current-servlet-request false)]
    (if (and session (session/using-servlet-session? session))
      (update-in response [:headers "Set-Cookie"]
                 #(remove
                   (partial cookie-matches-servlet-session-cookie? session)
                   %))
      response)))

(defn ^:internal servlet-session-wrapper
  "Middleware that attempts to prevent duplicate cookies when the
  servlet session is being used. If the servlet session is active and
  the response includes a cookie with the same name and id, it is
  stripped."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (servlet-cookie-dedup-handler response))))
