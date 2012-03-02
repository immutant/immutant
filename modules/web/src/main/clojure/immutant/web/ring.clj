;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.ring
  (:use [immutant.web.core            :only [get-servlet-filter current-servlet-request]]
        [immutant.web.session.handler :only [remove-ring-session-cookie]])
  (:require [clojure.string    :as string]
            [ring.util.servlet :as servlet])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)))

(defn- ensure-slash [^String s]
  (if (.startsWith s "/")
    s
    (str "/" s)))

(defn shuffle-sub-context [sub-context context path-info]
  (let [prefix (string/replace sub-context #"/\*$" "")]
    {:context (ensure-slash
               (str (if (and (not (empty? prefix))
                             (= "/" context)) "" context) prefix))
     :path-info (ensure-slash
                 (string/replace path-info (re-pattern (str "^" prefix)) ""))}))

(defn handle-request [filter-name
                      sub-context
                      ^HttpServletRequest request
                      ^HttpServletResponse response]
  (.setCharacterEncoding response "UTF-8")
  (let [{:keys [handler response-filters]} (get-servlet-filter filter-name)]
    (if handler
      (if-let [response-map (binding [current-servlet-request request]
                              ((remove-ring-session-cookie handler)
                               (merge (servlet/build-request-map request)
                                      (shuffle-sub-context sub-context
                                                           (.getContextPath request)
                                                           (.getPathInfo request)))))]
        (servlet/update-servlet-response response response-map)
        (throw (NullPointerException. "Handler returned nil.")))
      (throw (IllegalArgumentException. (str "No handler function found for " filter-name))))))

