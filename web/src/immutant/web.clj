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
                                             validate-options validate-options*
                                             valid-options opts->set]]
            [immutant.util           :refer [mapply dev-mode?]]
            [immutant.web.middleware :refer [add-middleware]]
            [clojure.walk            :refer [keywordize-keys]])
  (:import org.projectodd.wunderboss.WunderBoss
           io.undertow.server.HttpHandler
           javax.servlet.Servlet
           [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]))

(defprotocol Handler
  (-mount [handler options])
  (-validate-options [handler options]))

(defn ^{:valid-options (conj (opts->set Web$CreateOption) :name :server)}
  server
  "Create an HTTP server or return existing one matching :name (defaults to \"default\").
   Any options here are applied to the server with the given name,
   but only if it has not yet been instantiated."
  ([] (server nil))
  ([opts]
     (if-let [result (:server opts)]
       result
       (let [opts (->> (keywordize-keys opts)
                    (merge {:name "default" :host "localhost" :port 8080})
                    (validate-options server))]
         (WunderBoss/findOrCreateComponent Web
           (:name opts)
           (extract-options opts Web$CreateOption))))))

(defn ^{:valid-options (set (concat (valid-options #'server)
                              (opts->set Web$RegisterOption)))}
  mount
  "Mounts the given handler.
   handler can be a ring handler fn, a servlet, or an undertow HttpHandler.
   Option docs coming soon."
  ([handler]
     (mount handler nil))
  ([handler options]
     (-mount handler options)))

(defmacro run
  "Composes server and mount fns; ensures handler is var-quoted"
  ([handler] `(run ~handler nil))
  ([handler options]
     (let [handler (if (and (dev-mode?)
                         (symbol? handler)
                         (not (get &env handler))
                         (resolve handler))
                     `(var ~handler)
                     handler)]
       `(let [options# (-validate-options ~handler (keywordize-keys ~options))]
          (mount ~handler options#)))))

(defn unmount
  "Unmount handler at context path"
  ([] (unmount "/"))
  ([context-path] (unmount context-path (server)))
  ([context-path server]
     (.unregister server context-path)))

(extend-protocol Handler

  javax.servlet.Servlet
  (-mount [servlet opts]
    (let [opts (-validate-options servlet opts)]
      (.registerServlet (server opts)
        servlet (extract-options opts Web$RegisterOption))))
  (-validate-options [_ opts]
    (validate-options mount opts))

  io.undertow.server.HttpHandler
  (-mount [handler opts]
    (let [opts (-validate-options handler opts)]
      (.registerHandler (server opts)
        handler (extract-options opts Web$RegisterOption))))
  (-validate-options [_ opts]
    (validate-options mount opts))

  clojure.lang.IFn
  (-mount [f opts]
    (let [opts (-validate-options f opts)]
      (mount (undertow/create-http-handler (add-middleware f opts)) opts)))
  (-validate-options [_ opts]
    (validate-options* "mount"
      (concat-valid-options #'mount #'add-middleware)
      opts)))
