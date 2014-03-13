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
  (:require [immutant.logging       :as log]
            [immutant.web.servlet   :as servlet]
            [immutant.util          :as util])
  (:use [immutant.web.internal    :only [start* stop*]]
        [immutant.web.middleware  :only [add-middleware]])
  (:import javax.servlet.http.HttpServletRequest))

(defn start-servlet
  "Can be used to mount a servlet in lieu of a typical Ring handler"
  [sub-context-path servlet]
  (log/info (format "Starting servlet for %s at: %s%s" (util/app-name)
              (util/app-uri) sub-context-path))
  (start* sub-context-path
          (servlet/proxy-servlet servlet)
          {}))

(defn start-handler
  "Typically not called directly; use start instead"
  [sub-context-path handler & {:as opts}]
  (log/info (format "Starting ring handler for %s at: %s%s" (util/app-name)
              (util/app-uri) sub-context-path))
  (start* sub-context-path
          (servlet/create-servlet (add-middleware handler opts))
          opts))

(defmacro start
  "Starts a Ring handler that will be called when requests
   are received on the given sub-context-path. If no sub-context-path
   is given, \"/\" is assumed.

   The options are a subset of those for ring-server [default]:
     :init          function called after handler is initialized [nil]
     :destroy       function called after handler is stopped [nil]
     :stacktraces?  display stacktraces when exception is thrown [true in :dev]
     :auto-reload?  automatically reload source files [true in :dev]
     :reload-paths  seq of src-paths to reload on change [dirs on classpath]"
  {:arglists '([handler] [handler options] [path handler options])}
  [& args]
  (let [[path args] (if (even? (count args))
                      [(first args) (next args)]
                      ["/" args])
        [handler & opts] args]
    (if (and (symbol? handler) (resolve handler))
      `(start-handler ~path (var ~handler) ~@opts)
      `(start-handler ~path ~handler ~@opts))))

(defn stop
  "Stops the Ring handler or servlet mounted at the given sub-context-path.
   If no sub-context-path is given, \"/\" is assumed."
  ([]
     (stop "/"))
  ([sub-context-path]
     (log/info (str "Stopping ring handler/servlet at URL: " (util/app-uri) sub-context-path))
     (stop* sub-context-path)))

(defn ^HttpServletRequest current-servlet-request
  "Returns the currently active HttpServletRequest. This will only
  return a value within an active ring handler. Standard ring handlers
  should never need to access this value."
  []
  immutant.web.internal/current-servlet-request)
