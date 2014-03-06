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

(ns immutant.web
  "Associate one or more Ring handlers with your application, mounted
   at unique context paths"
  (:require [immutant.logging        :as log]
            [immutant.util           :as util]
            [immutant.web.undertow   :as undertow]
            [clojure.walk            :refer [stringify-keys]]
            [immutant.web.middleware :refer [add-middleware]])
  (:import org.projectodd.wunderboss.WunderBoss))

(defn server
  "Create an HTTP server"
  [& {:as opts}]
  (WunderBoss/findOrCreateComponent "web"
    (stringify-keys
      (merge {:name "default" :host "localhost" :port 8080} opts))))

;; TODO: options
(defn mount-handler
  "Mount a Ring handler at a context path on a server"
  ([server handler] (mount-handler server handler "/"))
  ([server handler context-path]
     (.registerHandler server context-path
       (undertow/create-http-handler handler)
       ;; options
       nil)))

(defn mount-servlet [])

(defn unmount
  [server context-path]
  (.unregister server context-path))

(defn run
  "Creates a server, mounts the handler at root context and runs it"
  [handler & opts]
  (mount-handler (apply server opts) handler))

;;; TODO: start-servlet and current-servlet-request (maybe)

;; (defn ^:internal start-handler
;;   "Typically not called directly; use start instead"
;;   [context-path handler & {:as opts}]
;;   (log/info (format "Starting ring handler for %s at: %s%s" (util/app-name)
;;               (util/app-uri) context-path))
;;   (wboss/start context-path (add-middleware handler opts) opts))

;; (defmacro start
;;   "Starts a Ring handler that will be called when requests
;;    are received on the given context-path. If no context-path
;;    is given, \"/\" is assumed.

;;    The options are a subset of those for ring-server [default]:
;;      :init          function called after handler is initialized [nil]
;;      :destroy       function called after handler is stopped [nil]
;;      :stacktraces?  display stacktraces when exception is thrown [true in :dev]
;;      :auto-reload?  automatically reload source files [true in :dev]
;;      :reload-paths  seq of src-paths to reload on change [dirs on classpath]"
;;   {:arglists '([handler] [handler options] [path handler options])}
;;   [& args]
;;   (let [[path args] (if (even? (count args))
;;                       [(first args) (next args)]
;;                       ["/" args])
;;         [handler & opts] args]
;;     (if (symbol? handler)
;;       `(start-handler ~path (var ~handler) ~@opts)
;;       `(start-handler ~path ~handler ~@opts))))

;; (defn stop
;;   "Stops the Ring handler or servlet mounted at the given context-path.
;;    If no context-path is given, \"/\" is assumed."
;;   ([]
;;      (stop "/"))
;;   ([context-path]
;;      (log/info (str "Stopping ring handler/servlet at URL: " (util/app-uri) context-path))
;;      (wboss/stop context-path)))
