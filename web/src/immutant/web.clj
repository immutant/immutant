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
  (:require [immutant.web.undertow.http :as undertow]
            [immutant.internal.util  :refer [concat-valid-options extract-options
                                             validate-options opts->set]]
            [immutant.util           :refer [mapply dev-mode?]]
            [immutant.web.middleware :refer [add-middleware]]
            [clojure.walk            :refer [keywordize-keys]])
  (:import org.projectodd.wunderboss.WunderBoss
           io.undertow.server.HttpHandler
           javax.servlet.Servlet
           [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]))

(defprotocol Handler
  (mount [handler] [handler options]))

(defn server
  "Create an HTTP server or return existing one matching :name (defaults to \"default\").
   Any options here are applied to the server with the given name,
   but only if it has not yet been instantiated."
  ([{:as opts}]
     (if-let [result (:server opts)]
       result
       (let [opts (->> (keywordize-keys opts)
                    (merge {:name "default" :host "localhost" :port 8080}))]
         (WunderBoss/findOrCreateComponent Web
           (:name opts)
           (extract-options opts Web$CreateOption)))))
  ([] (server {})))

(extend-protocol Handler

  javax.servlet.Servlet
  (mount
    ([this]
       (mount this {}))
    ([this opts]
      (.registerServlet (server opts) this (extract-options opts Web$RegisterOption))))

  io.undertow.server.HttpHandler
  (mount
    ([this]
       (mount this {}))
    ([this opts]
       (.registerHandler (server opts) this (extract-options opts Web$RegisterOption))))
  
  clojure.lang.IFn
  (mount
    ([this]
       (mount this {}))
    ([this opts]
      (.registerHandler (server opts)
        (undertow/create-http-handler (add-middleware this opts))
        (extract-options opts Web$RegisterOption)))))

(defmacro run
  "Composes server and mount fns; ensures handler is var-quoted"
  ([handler] `(run ~handler {}))
  ([handler options]
     (let [handler (if (and (dev-mode?)
                         (symbol? handler)
                         (not (get &env handler))
                         (resolve handler))
                     `(var ~handler)
                     handler)]
       `(let [options# (keywordize-keys ~options)]
          (mount ~handler options#)))))

(defn unmount
  "Unmount handler at context path"
  ([] (unmount "/"))
  ([context-path] (unmount context-path (server)))
  ([context-path server]
     (.unregister server context-path)))
