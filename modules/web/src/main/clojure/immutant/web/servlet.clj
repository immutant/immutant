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

(ns ^{:no-doc true} immutant.web.servlet
  (:use [immutant.web.internal :only [current-servlet-request]]
        [immutant.util :only [with-tccl]])
  (:require [ring.util.servlet :as servlet])
  (:import javax.servlet.http.HttpServletRequest))

(defn- context [^HttpServletRequest request]
  (str (.getContextPath request)
       (.getServletPath request)))

;; we don't use .getPathInfo since it is decoded. See IMMUTANT-195
(defn- path-info [^HttpServletRequest request]
  (let [path-info (.substring (.getRequestURI request)
                              (.length (context request)))]
    (if (.isEmpty path-info)
      "/"
      path-info)))

(defn create-servlet [handler]
  (reify javax.servlet.Servlet
    (service [_ request response]
      (with-tccl
        (.setCharacterEncoding response "UTF-8")
        (if-let [response-map (binding [current-servlet-request request]
                                (handler
                                 (assoc (servlet/build-request-map request)
                                   :context (context request)
                                   :path-info (path-info request))))]
          (servlet/update-servlet-response response response-map)
          (throw (NullPointerException. "Handler returned nil.")))))
    (init [_ _])
    (destroy [_])))

(deftype ServletProxy [servlet]
  javax.servlet.Servlet
  (init [_ config]
    (with-tccl (.init servlet config)))
  (service [_ request response]
    (with-tccl (.service servlet request response)))
  (destroy [_]
    (.destroy servlet))
  (getServletConfig [_]
    (.getServletConfig servlet))
  (getServletInfo [_]
    (.getServletInfo servlet)))

(defn proxy-servlet [servlet]
  (ServletProxy. servlet))