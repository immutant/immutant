;; Copyright 2014 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.wildfly.repl
  (:require [clojure.tools.nrepl.server :as nrepl]
            [immutant.util :as u]))

(defn ^:private spit-nrepl-files
  [port file]
  (doseq [f (map u/app-relative
                 (if file
                   [file]
                   [".nrepl-port" "target/repl-port"]))]
    (.mkdirs (.getParentFile f))
    (spit f port)
    (.deleteOnExit f)))

(defn ^:private nrepl-host-port [server]
  (let [ss (-> server deref :ss)]
    [(-> ss .getInetAddress .getHostAddress) (.getLocalPort ss)]))

(defn stop [server]
  (println "Shutting down nREPL at" (apply format "%s:%s" (nrepl-host-port server)))
  (.close server))

;; TODO: bring over the 1.x impl for middleware, et al
(defn start [{:keys [host port]}]
  (let [server (nrepl/start-server :port (or port 0) :bind (or host "localhost"))]
    (u/at-exit (partial stop server))
    (let [[host bound-port] (nrepl-host-port server)]
      (println "nREPL bound to" (format "%s:%s" host bound-port))
          (spit-nrepl-files bound-port nil ;(:nrepl-port-file config)
            ))))
