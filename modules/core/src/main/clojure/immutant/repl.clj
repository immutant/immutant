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
  (:require [immutant.utilities         :as util]
            [clojure.tools.logging      :as log]))

(defn ^{:private true} fix-port [port]
  (if (= String (class port))
    (Integer. port)
    port))

;; We have to bind the repl * vars under 1.2.1, otherwise swank fails to start,
;; and with-bindings NPE's, so we roll our own
(defmacro with-base-repl-bindings [& body]
  `(binding [*e nil
             *1 nil
             *2 nil
             *3 nil]
     ~@body))

(defn stop-swank
  "Shuts down the running swank server."
  []
  (log/info "Stopping swank for" (util/app-name))
  ((util/try-resolve 'swank.swank/stop-server)))

(defn start-swank
  "Starts a swank server on the given port. If an interface-address is provided,
the server is bound to that interface. Otherwise it binds to the management
interface defined by the AS. Registers an at-exit handler to shutdown swank on
undeploy."
  ([interface-address port]
     (log/info "Starting swank for" (util/app-name) "at" (str interface-address ":" port))
     (with-base-repl-bindings
       ((util/try-resolve 'swank.swank/start-server) :host interface-address :port (fix-port port) :exit-on-quit false))
     (util/at-exit stop-swank))
  ([port]
     (start-swank (util/management-interface-address) port)))

(defn stop-nrepl
  "Stops the given nrepl server."
  [server]
  (log/info "Stopping nrepl for" (util/app-name))
  (.close (:ss @server)))

(defn start-nrepl
  "Starts an nrepl server on the given port. If an interface-address is
provided, the server is bound to that interface. Otherwise it binds to the
management interface defined by the AS. Registers an at-exit handler to
shutdown nrepl on undeploy, and returns a server that can be passed to
stop-nrepl to shut it down manually."
  ([interface-address port]
     (log/info "Starting nrepl for" (util/app-name) "at" (str interface-address ":" port))
     (when-let [server ((util/try-resolve 'clojure.tools.nrepl.server/start-server)
                        :port (fix-port port) :host interface-address)]
       (util/at-exit (partial stop-nrepl server))
       server))
  ([port]
     (start-nrepl (util/management-interface-address) port)))

(defn ^{:internal true} init-repl
  "Looks for nrepl-port and swank-port values in the given config, and starts
the appropriate servers."
  [config]
  (when-let [port (config "nrepl-port")]
    (start-nrepl port))
  (when-let [port (config "swank-port")]
    (start-swank port)))
