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

(ns immutant.web
  "Associate one or more Ring handlers with your application, mounted
   at unique context paths"
  (:require [immutant.logging        :as log]
            [immutant.web.undertow   :as undertow]
            [immutant.web.websocket  :as ws]
            [immutant.internal.util  :refer [concat-valid-options extract-options
                                             validate-options opts->set]]
            [immutant.util           :refer [mapply dev-mode?]]
            [immutant.web.middleware :refer [add-middleware]]
            [clojure.walk            :refer [keywordize-keys]]
            [ring.util.response      :refer [response]])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]))

(defn ^{:valid-options (conj (opts->set Web$CreateOption) :name)}
  server
  "Create an HTTP server or return existing one matching :name (defaults to \"default\").
   Any options here are applied to the server with the given name,
   but only if it has not yet been instantiated."
  [& {:as opts}]
  (let [opts (->> (keywordize-keys opts)
               (merge {:name "default" :host "localhost" :port 8080})
               (validate-options server))]
    (WunderBoss/findOrCreateComponent Web
      (:name opts)
      (extract-options opts Web$CreateOption))))

(defn ^{:valid-options (conj (opts->set Web$RegisterOption)
                         :stacktraces? :auto-reload? :reload-paths)}
  mount
  "Mount a Ring handler on a server"
  [server handler & {:as opts}]
  (let [opts (->> (keywordize-keys opts)
               (merge {:context-path "/"})
               (validate-options mount))]
    (.registerHandler server
      (undertow/create-http-handler (add-middleware handler opts))
      (extract-options opts Web$RegisterOption))))

(defmacro ^{:valid-options (concat-valid-options #'server #'mount)}
  run
  "Composes server and mount fns; ensures handler is var-quoted"
  ([handler] `(run ~handler {}))
  ([handler options]
     (let [handler (if (and (dev-mode?)
                         (symbol? handler)
                         (not (get &env handler))
                         (resolve handler))
                     `(var ~handler)
                     handler)]
       `(let [options# (validate-options run (keywordize-keys ~options))]
          (mapply mount (mapply server options#) ~handler options#)))))

(defn unmount
  "Unmount handler at context path"
  ([] (unmount "/"))
  ([context-path] (unmount context-path (server)))
  ([context-path server]
     (.unregister server context-path)))

(defn ^{:valid-options #{:context-path}}
  mount-servlet
  "Mount a servlet on a server"
  [server servlet & {:as opts}]
  (let [opts (->> (keywordize-keys opts)
               (merge {:context-path "/"})
               (validate-options mount-servlet))]
    (.registerServlet server servlet
      (extract-options opts Web$RegisterOption))))

(defn mount-websocket
  "Register callbacks for a websocket session. The only required one
  is the single-arity on-message. The signatures for the optional ones
  are listed in the :or clause."
  [server on-message
   & {:keys [on-open on-close on-error fallback]
      :or {on-open  (fn [channel]
                      (log/info "on-open" channel))
           on-close (fn [channel {c :code r :reason}]
                      (log/info "on-close" channel c r))
           on-error (fn [channel error]
                      (log/error "on-error" channel error))
           fallback (fn [request]
                      (response "Unable to create websocket"))}
      :as opts}]
  (let [opts (->> (keywordize-keys opts)
               (merge {:context-path "/"}))]
    (.registerHandler server
      (ws/create-handler :on-message on-message, :on-open on-open,
        :on-close on-close, :on-error on-error, :fallback fallback)
      (extract-options opts Web$RegisterOption))))
