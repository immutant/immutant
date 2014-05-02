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
            [immutant.web.internal     :refer :all]
            [clojure.walk              :refer [keywordize-keys]])
  (:import [org.projectodd.wunderboss.web Web$CreateOption Web$RegisterOption]))

(defn run
  "Runs a handler with the given options.

   The handler can be a Ring handler function, a Servlet, or an
   Undertow HttpHandler. Can be called multiple times - if given the
   same env, any prior handler with that env will be replaced. Returns
   the given env with any missing defaults filled in.

   Needs: options, examples"
  ([handler] (run nil handler))
  ([env handler]
     (let [options (->> env
                     keywordize-keys
                     (merge create-defaults register-defaults))]
       (validate-options options run)
       (mount (server options) handler options)
       options)))

(set-valid-options! run (opts->set Web$CreateOption Web$RegisterOption))

(defn stop
  "Stops a running handler.

   The handler-env argument is a map, typically the return value of a
   run call. If that return value is not available, you can pass the
   same env map passed to run for the handler you want to stop. If
   handler-env isn't provided, the handler at the root context (\"/\")
   of the default server will be stopped. If there are no handlers
   remaining on the server, the server itself is stopped. Returns true
   if a handler was actually removed."
  ([]
     (stop nil))
  ([handler-env]
     (let [options (-> handler-env
                     keywordize-keys
                     (validate-options run "stop"))
           server (server options)
           stopped (.unregister server
                     (:context-path options (:context-path register-defaults)))]
       (if (empty? (.registeredContexts server))
         (.stop server))
       stopped)))

(defmacro run-dmc
  "Run in Development Mode (the 'C' is silent).

   This macro invokes run after ensuring the passed handler is
   var-quoted, with reload and stacktrace middleware applied, and then
   opens the app in a browser. Supports the same options as run."
  ([handler] `(run-dmc nil ~handler))
  ([env handler]
     (let [handler (if (and (symbol? handler)
                         (not (get &env handler))
                         (resolve handler))
                     `(var ~handler)
                     handler)]
       `(run-dmc* run ~env ~handler))))
