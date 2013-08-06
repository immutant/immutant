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

(ns immutant.repl
  "Provides tools for starting swank and nrepl servers."
  (:require [immutant.util         :as util]
            [immutant.registry     :as registry]
            [clojure.tools.logging :as log]))

(defn ^:private fix-port [port]
  (if (string? port)
    (Integer. port)
    port))

(defn ^:private nrepl-init-handler
  "Provides an init point for new nrepl connections."
  [h]
  (fn [{:keys [op transport] :as msg}]
    (when (= op "clone")
      (require 'clj-stacktrace.repl 'complete.core))
    (h msg)))

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
     ((util/try-resolve 'swank.swank/start-server) :host interface-address :port (fix-port port) :exit-on-quit false)
     (util/at-exit stop-swank))
  ([port]
     (start-swank (util/management-interface-address) port)))

(defn stop-nrepl
  "Stops the given nrepl server."
  [server]
  (log/info "Stopping nrepl for" (util/app-name))
  (.close server))

(defn start-nrepl
  "Starts an nrepl server on the given port. If an interface-address is
provided, the server is bound to that interface. Otherwise it binds to the
management interface defined by the AS. Registers an at-exit handler to
shutdown nrepl on undeploy, and returns a server that can be passed to
stop-nrepl to shut it down manually."
  ([interface-address port]
   ;; add the needed metadata for an nrepl middleware, but only do it if
   ;; nrepl is going to be used to avoid loading nrepl at deploy for every app
   (when-not (::descriptor (meta #'nrepl-init-handler))
     ((util/try-resolve 'clojure.tools.nrepl.middleware/set-descriptor!)
      #'nrepl-init-handler {}))
   (let [{{:keys [nrepl-middleware nrepl-handler]} :repl-options}
         (registry/get :project)
         require-resolve #(do (-> % namespace symbol require)
                              (resolve %))]
     (log/info "Starting nREPL for" (util/app-name)
               "at" (str interface-address ":" port))
     (when (and nrepl-middleware nrepl-handler)
       ;; TODO appropriate exception type here?
       (throw (IllegalStateException.
                "Can only use one of :nrepl-handler or :nrepl-middleware")))
     (let [handler (or (and nrepl-handler (require-resolve nrepl-handler))
                       (->> nrepl-middleware
                            (map #(cond 
                                    (var? %) %
                                    (symbol? %) (require-resolve %)
                                    (list? %) (eval %)))
                            (apply (util/try-resolve
                                     'clojure.tools.nrepl.server/default-handler)
                                   #'nrepl-init-handler)))]
       (when-let [server ((util/try-resolve 'clojure.tools.nrepl.server/start-server)
                          :handler handler
                          :port (fix-port port)
                          :bind interface-address)]
         (util/at-exit (partial stop-nrepl server))
         server))))
  ([port]
   (start-nrepl (util/management-interface-address) port)))

(defn ^:private spit-nrepl-file
  [server file]
  (let [ss (-> server deref :ss)
        port (.getLocalPort ss)
        host (-> ss .getInetAddress .getHostAddress)
        file (util/app-relative (or file "target/repl-port"))]
    (log/info "Bound to" (str host ":" port))
    (.mkdirs (.getParentFile file))
    (spit file port)
    (.deleteOnExit file)))

(defn ^{:internal true :no-doc true} init-repl
  "Looks for nrepl-port and swank-port values in the given config, and starts
the appropriate servers."
  [config]
  (when-let [port (config "nrepl-port")]
    (spit-nrepl-file (start-nrepl port) (config "nrepl-port-file")))
  (when-let [port (config "swank-port")]
    (start-swank port)))
