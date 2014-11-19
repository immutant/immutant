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

(ns ^:no-doc immutant.wildfly.repl
    (:require [clojure.tools.nrepl.server :as nrepl]
              [clojure.java.io            :as io]
              [immutant.util              :as u]
              [immutant.internal.util     :as iu]
              [wunderboss.util            :as wu]))

(defn ^:private possible-nrepl-files [file]
  (let [file (io/file file)]
    (if (and file (.isAbsolute file))
      [file]
      (when (u/app-root)
        (map u/app-relative
          (if file
            [file]
            [".nrepl-port" "target/repl-port"]))))))

(defn ^:private spit-nrepl-files
  [port file]
  (doseq [^java.io.File f (possible-nrepl-files file)]
    (.mkdirs (.getParentFile f))
    (spit f port)
    (.deleteOnExit f)))

(defn ^:private nrepl-host-port [server]
  (let [ss (-> server deref :ss)]
    [(-> ss .getInetAddress .getHostAddress) (.getLocalPort ss)]))

(defn stop
  "Stop the REPL"
  [server]
  (iu/info (apply format "Shutting down nREPL at %s:%s" (nrepl-host-port server)))
  (.close server))

(defn start
  "Fire up a repl bound to host/port"
  [{:keys [host port port-file] :or {host "localhost", port 0}}]
  (let [{:keys [nrepl-middleware nrepl-handler]}
        (-> (wu/options) (get "repl-options" "{}") read-string)]
    (when (and nrepl-middleware nrepl-handler)
      (throw (IllegalArgumentException.
               "Can only use one of :nrepl-handler or :nrepl-middleware")))
    (let [handler (or (and nrepl-handler (iu/require-resolve nrepl-handler))
                    (->> nrepl-middleware
                      (map #(cond
                              (var? %) %
                              (symbol? %) (iu/require-resolve %)
                              (list? %) (eval %)))
                      (apply nrepl/default-handler)))
          server (nrepl/start-server :port port :bind host :handler handler)]
      (u/at-exit (partial stop server))
      (let [[host bound-port] (nrepl-host-port server)]
        (iu/info (format "nREPL bound to %s:%s" host bound-port))
        (spit-nrepl-files bound-port port-file)))))
