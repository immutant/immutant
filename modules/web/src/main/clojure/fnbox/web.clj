;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

(ns fnbox.web
  (:require [ring.util.servlet :as servlet])
  (:require [clojure.string :as str])
  (:require [fnbox.utilities :as util])
  (:import (javax.servlet.http HttpServletRequest
                               HttpServletResponse)))

(defn handler [app]
  (fn [^HttpServletRequest request
       ^HttpServletResponse response]
    (.setCharacterEncoding response "UTF-8")
    (if-let [response-map (app (servlet/build-request-map request))]
      (servlet/update-servlet-response response response-map)
      (throw (NullPointerException. "Handler returned nil.")))))

(defn handle-request [app-function request response]
  ((handler (util/load-and-invoke app-function)) request response))
