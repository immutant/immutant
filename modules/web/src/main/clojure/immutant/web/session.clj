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

(ns immutant.web.session
  "Functions for using the cluster-wide servlet store for sessions."
  (:use ring.middleware.session.store
        [immutant.web.internal :only [current-servlet-request]])
  (:require [ring.middleware.session :as ring-session]
            [immutant.registry       :as registry])
  (:import javax.servlet.http.HttpSession))

(def ^{:private true} session-key ":immutant.web.session/session-data")

(defn using-servlet-session? [session]
  "Returns true if the given servlet session is being used to store the ring session."
  (and session
       (not (nil? (.getAttribute session session-key)))))

(defn #^HttpSession servlet-session
  "Returns the servlet session for the current request."
  []
  (and current-servlet-request
       (.getSession current-servlet-request)))

(deftype
    ^{:doc "A ring SessionStore implementation that uses the session provided by the servlet container."}
    ServletStore [] SessionStore
  (read-session [_ _]
    (let [session (servlet-session)]
      (if-let [data (and session 
                         (.getAttribute session session-key))]
        data
        {})))
  (write-session [_ _ data]
    ;; TODO: it may be useful to store data as entries directly in the
    ;; session. TorqueBox does this to share the session data with
    ;; java servlet components.
    (when-let [session (servlet-session)]
      (.setAttribute session session-key data)
      (.getId session)))
  (delete-session [_ _]
    (when-let [session (servlet-session)]
      (.removeAttribute session session-key)
      (.invalidate session))))

(defn servlet-store
  "Instantiates and returns a ServletStore"
  []
  (ServletStore.))

(defn set-session-timeout!
  "Sets the session timeout (in minutes), overriding the default of 30
  minutes. Applies to all of the web endpoints deployed within an
  application, since they all share the same session."
  [minutes]
  (when-let [ctx (registry/get "web-context")]
    (.setSessionTimeout ctx minutes)))

(defn set-session-cookie-attributes!
  "Set session cookie attributes. Accepts the following kwargs, many
   of which are analagous to the ring session cookie attributes
   [default]:

   :cookie-name  The name of the cookie [JSESSIONID]
   :domain       The domain name where the cookie is valid [nil]
   :path         The path where the cookie is valid [the context path]
   :http-only    Should the cookie be used only for http? [false]
   :secure       Should the cookie be used only for secure connections? [false]
   :max-age      The amount of time the cookie should be retained by the
                 client, in seconds [-1, meaning 'never expire']

   This function can be called multiple times, and will only alter the
   attributes passed to it.

   These attributes apply to all of the web endpoints deployed within
   application, since they all share the same session."
  [& {:as attrs}]
  (when-let [ctx (registry/get "web-context")]
    (let [cookie (.getSessionCookie ctx)
          set-param (fn [key f]
                      (when (contains? attrs key)
                        (f cookie (attrs key))))]
      (set-param :cookie-name #(.setName %1 %2))
      (set-param :domain      #(.setDomain %1 %2))
      (set-param :path        #(.setPath %1 %2))
      (set-param :http-only   #(.setHttpOnly %1 (boolean %2)))
      (set-param :secure      #(.setSecure %1 (boolean %2)))
      (set-param :max-age     #(.setMaxAge %1 %2)))))



