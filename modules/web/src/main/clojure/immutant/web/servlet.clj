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

(ns ^{:no-doc true} immutant.web.servlet
  (:use [immutant.web.internal :only [current-servlet-request]]
        [immutant.web.session.internal :only [servlet-session-wrapper]]
        [immutant.util :only [with-tccl]])
  (:require [ring.util.servlet :as servlet])
  (:import javax.servlet.Servlet
           javax.servlet.http.HttpServletRequest
           org.immutant.web.ReusableInputStream))

(defn- ^String context [^HttpServletRequest request]
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
        (let [request-map (-> request
                            servlet/build-request-map
                            (assoc
                                :context (context request)
                                :path-info (path-info request))
                            (update-in [:body] #(ReusableInputStream. %)))]
          (with-open [_ (.closer ^ReusableInputStream (:body request-map))]
            (if-let [response-map (binding [current-servlet-request request]
                                    ((servlet-session-wrapper handler) request-map))]
              (servlet/update-servlet-response response response-map)
              (throw (NullPointerException. "Ring handler returned nil instead of a response map.")))))))
    (init [_ _])
    (destroy [_])))

(deftype ServletProxy [^Servlet servlet]
  Servlet
  (init [_ config]
    (with-tccl (.init servlet config)))
  (service [_ request response]
    (with-tccl
      (binding [current-servlet-request request]
        (.service servlet request response))))
  (destroy [_]
    (.destroy servlet))
  (getServletConfig [_]
    (.getServletConfig servlet))
  (getServletInfo [_]
    (.getServletInfo servlet)))

(defn proxy-servlet [servlet]
  (ServletProxy. servlet))
