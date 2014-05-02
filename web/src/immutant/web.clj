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
  (:require [immutant.internal.options :refer [opts->set set-valid-options!
                                               validate-options]]
            [immutant.web.internal     :refer [default-context mount server]]
            [immutant.web.middleware   :refer [wrap-dev-middleware]]
            [clojure.walk              :refer [keywordize-keys]]
            [clojure.java.browse       :refer [browse-url]])
  (:import [org.projectodd.wunderboss.web Web$CreateOption Web$RegisterOption]))

(defn run
  "Runs a handler with the given options.
   The handler can be a ring handler function, a servlet, or an Undertow
   HttpHandler. Can be called multiple times - if given the same env,
   any prior handler with that env will be replaced. Returns the given
   env with the server object added under :server.
   Needs: options, examples"
  ([handler] (run nil handler))
  ([env handler]
     (let [options (keywordize-keys env)]
       (validate-options options run)
       (let [server (server options)]
         (mount server handler options)
         (assoc options :server server)))))

(set-valid-options! run
  (-> (concat
        (opts->set Web$CreateOption)
        (opts->set Web$RegisterOption))
    (conj :server)
    set))

(defn stop
  "Stops a running handler.
   handler-env should be the return value of a run call. If that return
   value is not available, you can pass the same env map passed to run for
   the handler you want to stop. If handler-env isn't provided, the handler
   at the root context (\"/\") of the default server will be stopped. If
   there are no handlers remaning on the server, the server itself is stopped."
  ([]
     (stop nil))
  ([handler-env]
     (let [options (-> handler-env
                     keywordize-keys
                     (validate-options run "stop"))
           server (server options)]
       (.unregister server (:context-path options default-context))
       (if (empty? (.registeredContexts server))
         (.stop server)))
     nil))

(defmacro run-dmc
  "Run in Development Mode (the 'C' is silent).
   This macro invokes run after ensuring the passed handler is
   var-quoted, with reload and stacktrace middleware applied, and then
   opens the app in a browser. Supports the same options as run."
  ([handler] `(run-dmc {} ~handler))
  ([env handler]
     (let [handler (if (and (symbol? handler)
                         (not (get &env handler))
                         (resolve handler))
                     `(var ~handler)
                     handler)]
       `(do
          (run ~env (wrap-dev-middleware ~handler))
          (browse-url (format "http://%s:%s%s"
                        (:host         ~env (.defaultValue Web$CreateOption/HOST))
                        (:port         ~env (.defaultValue Web$CreateOption/PORT))
                        (:context-path ~env default-context)))))))
