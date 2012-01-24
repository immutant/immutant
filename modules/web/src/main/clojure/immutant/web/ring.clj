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
  (:require [ring.util.servlet :as servlet])
  (:use [immutant.web.core :only [filters]]))

(def ^{:dynamic true} current-servlet-request nil)

(defn handle-request [filter-name request response]
  (.setCharacterEncoding response "UTF-8")
  (if-let [handler (get-in @filters [filter-name :handler])]
    (if-let [response-map (binding [current-servlet-request request]
                            (handler (servlet/build-request-map request)))]
      (servlet/update-servlet-response response response-map)
      (throw (NullPointerException. "Handler returned nil.")))
    (throw (IllegalArgumentException. (str "No handler function found for " filter-name)))))
