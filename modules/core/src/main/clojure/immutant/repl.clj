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

(ns immutant.repl
  "Provides tools for starting nrepl servers."
  (:require [immutant.util         :as util]
            [immutant.registry     :as registry]
            [immutant.logging      :as log]))

(defn ^:private fix-port [port]
  (if (string? port)
    (Integer. port)
    port))

(defn stop-nrepl
  "Stops the given nrepl server."
  [server]
  (log/info "Stopping nrepl for" (util/app-name))
  (.close server))

(defn start-nrepl
  "Starts an nrepl server on the given port and interface.
   The interface can be an ip address string, or an alias to one of
   the interfaces defined by the AS: :public, :management,
   or :unsecure. If no interface is provided, it binds to
   the :management interface (which is 127.0.0.1 by
   default). Registers an at-exit handler to shutdown nrepl on
   undeploy, and returns a server that can be passed to stop-nrepl to
   shut it down manually."
  ([interface port]
     (let [{{:keys [nrepl-middleware nrepl-handler]} :repl-options}
         (registry/get :project)
         require-resolve #(do (-> % namespace symbol require)
                              (resolve %))
         interface-address (or (util/lookup-interface-address interface)
                               interface
                               (util/management-interface-address))]
       (if (nil? interface-address)
         (log/warn "Invalid interface address for nREPL; use :nrepl-interface")
         (do
           (log/info "Starting nREPL for" (util/app-name)
                     "at" (str interface-address ":" port))
           (future (require 'clj-stacktrace.repl 'complete.core))
           (when (and nrepl-middleware nrepl-handler)
             (throw (IllegalArgumentException.
                     "Can only use one of :nrepl-handler or :nrepl-middleware")))
           (let [handler (or (and nrepl-handler (require-resolve nrepl-handler))
                             (->> nrepl-middleware
                                  (map #(cond 
                                         (var? %) %
                                         (symbol? %) (require-resolve %)
                                         (list? %) (eval %)))
                                  (apply (util/try-resolve
                                          'clojure.tools.nrepl.server/default-handler))))]
             (when-let [server ((util/try-resolve 'clojure.tools.nrepl.server/start-server)
                                :handler handler
                                :port (fix-port port)
                                :bind interface-address)]
               (util/at-exit (partial stop-nrepl server))
               server))))))
  ([port]
   (start-nrepl nil port)))

(defn ^:private spit-nrepl-files
  [port file]
  (doseq [f (map util/app-relative
                 (if file
                   [file]
                   [".nrepl-port" "target/repl-port"]))]
    (.mkdirs (.getParentFile f))
    (spit f port)
    (.deleteOnExit f)))

(defn ^{:internal true :no-doc true} init-repl
  "Looks for nrepl-port value in the given config, and starts
the appropriate servers."
  [config]
  (let [ring-nrepl-config (-> (registry/get :project) :ring :nrepl)
        port (or (:nrepl-port config)
                 (and (:start? ring-nrepl-config)
                      (:port ring-nrepl-config))
                 (if (util/dev-mode?) 0 nil))
        interface (:nrepl-interface config)]
    (if (or port interface)
      (if-let [nrepl (start-nrepl interface (or port 0))]
        (let [ss (-> nrepl deref :ss)
              host (-> ss .getInetAddress .getHostAddress)
              bound-port (.getLocalPort ss)]
          (log/info "nREPL bound to" (str host ":" bound-port))
          (spit-nrepl-files bound-port (:nrepl-port-file config)))))))
