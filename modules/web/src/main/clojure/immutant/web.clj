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

(ns immutant.web
  "Associate one or more Ring handlers with your application, mounted
   at unique context paths"
  (:require [immutant.registry      :as registry]
            [clojure.tools.logging  :as log]
            [immutant.util          :as util]
            [ring.middleware.reload :as ring])
  (:use [immutant.web.internal :exclude [current-servlet-request]])
  (:import javax.servlet.http.HttpServletRequest))

(defn ^HttpServletRequest current-servlet-request
  "Returns the currently active HttpServletRequest. This will only
  return a value within an active ring handler. Standard ring handlers
  should never need to access this value."
  []
  immutant.web.internal/current-servlet-request)

(def
  ^{:arglists '([handler & {:keys [reload]}]
                [sub-context-path handler & {:keys [reload]}])
    :doc "Registers a Ring handler that will be called when requests
   are received on the given sub-context-path. If no sub-context-path
   is given, \"/\" is assumed.

   The following options are supported [default]:
     :reload    monitors the app's src/ dir for changes [false]"}
  start
  (fn [& args]
    (util/if-in-immutant
     (let [[sub-context-path args] (if (string? (first args))
                                     [(first args) (next args)]
                                     ["/" args])
           [handler & {:keys [reload]}] args
           handler (if reload
                     (ring/wrap-reload
                      handler
                      {:dirs [(util/app-relative "src")]})
                     handler)
           sub-context-path (normalize-subcontext-path sub-context-path)
           servlet-name (servlet-name sub-context-path)]
       (if-let [existing-info (get-servlet-info servlet-name)]
         (do
           (log/info "Updating ring handler at sub-context path:" sub-context-path)
           (store-servlet-info!
            servlet-name
            (assoc existing-info :handler handler)))
         (do
           (log/info "Registering ring handler at sub-context path:" sub-context-path)
           (store-servlet-info!
            servlet-name
            {:wrapper (install-servlet "org.immutant.web.servlet.RingServlet"
                                       sub-context-path)
             :sub-context sub-context-path
             :handler handler})))
       nil)
     (log/warn "web/start called outside of Immutant, ignoring"))))


(defn stop
  "Deregisters the Ring handler attached to the given sub-context-path.
   If no sub-context-path is given, \"/\" is assumed."
  ([]
     (stop "/"))
  ([sub-context-path]
     (util/if-in-immutant
      (let [sub-context-path (normalize-subcontext-path sub-context-path)]
        (if-let [info (remove-servlet-info! (servlet-name sub-context-path))]
          (do
            (log/info "Deregistering ring handler at sub-context path:" sub-context-path)
            (remove-servlet sub-context-path (:wrapper info)))
          (log/warn "Attempted to deregister ring handler at sub-context path:" sub-context-path ", but none found")))
      (log/warn "web/stop called outside of Immutant, ignoring"))))



