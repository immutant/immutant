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

(ns immutant.web.session.handler
  (:use [immutant.web.core :only [current-servlet-request]])
  (:require [immutant.utilities :as util]
            [immutant.web.session :as session]))

(def cookie-encoder (util/try-resolve-any
                     'ring.util.codec/form-encode  ;; ring >= 1.1.0
                     'ring.util.codec/url-encode))

;; TODO: this probably won't work if there are handlers active on the
;; same servlet with mixed use of the servlet session. If we move to a
;; servlet per web/start, this limitation will go away.
(defn servlet-session-wrapper
  "Middleware that handles cookie manipulation if the servlet session is used."
  [handler]
  (fn [request]
    (let [session (.getSession current-servlet-request false)
          response (handler
                    (if (session/using-servlet-session? session)
                      ;; we need to give ring the session id so it can know when
                      ;; it needs to clear the session
                      (assoc-in request [:cookies "ring-session" :value] (.getId session))
                      request))]
      ;; we can't use the session var above, since one may have been created
      ;; by the handler call
      (if-let [session (.getSession current-servlet-request false)]
        (update-in response [:headers "Set-Cookie"]
                   #(filter (fn [^String cookie]
                              (not (.contains cookie
                                              (cookie-encoder (.getId session))))) %))
        response))))


