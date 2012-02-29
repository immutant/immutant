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

(ns immutant.repl
  "Provides tools for starting swank and nrepl servers."
  (:require [swank.swank                   :as swank]
            [clojure.tools.nrepl.server    :as nrepl]
            [clojure.tools.nrepl.transport :as transport]
            [immutant.utilities            :as util]
            [clojure.tools.logging         :as log]))

(defn stop-swank
  "Shuts down the running swank server."
  []
  (log/info "Stopping swank for" (util/app-name))
  (swank/stop-server))

(defn start-swank
  "Starts a swank server on the given port. If an interface-address is provided,
the server is bound to that interface. Otherwise it binds to the management
interface defined by the AS. Registers an at-exit handler to shutdown swank on
undeploy."
  ([interface-address port]
     (log/info "Starting swank for" (util/app-name) "at" (str interface-address ":" port))
     (swank/start-server :host interface-address :port port :exit-on-quit false)
     (util/at-exit stop-swank))
  ([port]
     (start-swank (util/management-interface-address) port)))

(defn stop-nrepl
  "Stops the given nrepl server."
  [server]
  (log/info "Stopping nrepl for" (util/app-name))
  (nrepl/stop-server server))

(defn start-nrepl
  "Starts an nrepl server on the given port. If an interface-address is
provided, the server is bound to that interface. Otherwise it binds to the
management interface defined by the AS. Registers an at-exit handler to
shutdown nrepl on undeploy, and returns a server that can be passed to
stop-nrepl to shut it down manually."
  ([interface-address port]
     (log/info "Starting nrepl for" (util/app-name) "at" (str interface-address ":" port))
     (when-let [server (nrepl/start-server :port port :host interface-address)]
       (util/at-exit (partial stop-nrepl server))
       server))
  ([port]
     (start-nrepl (util/management-interface-address) port)))
