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

(ns immutant.web.session
  (:use ring.middleware.session.store
        [immutant.web.ring :only [current-servlet-request]])
  (:require [ring.middleware.session :as ring-session]))

(def session-key "immutant-session")

(defn servlet-session []
  (and current-servlet-request
       (.getSession current-servlet-request true)))

(deftype ServletStore []
  SessionStore
  (read-session [_ _]
    (let [session (servlet-session)]
      (if-let [data (and session 
                         (.getAttribute session session-key))]
        data
        {})))
  (write-session [_ _ data]
    (when-let [session (servlet-session)]
      (.setAttribute session session-key data)
      (.getId session)))
  (delete-session [_ _]
    (when-let [session (servlet-session)]
      (.removeAttribute session session-key)
      (.invalidate session))))

(defn servlet-store []
  (ServletStore.))

(defn wrap-session [handler]
  (ring-session/wrap-session handler {:store (servlet-store)
                                      :cookie-name nil}))

