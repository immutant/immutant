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
  (:require [immutant.opts-validation :refer [opts->set set-valid-options!
                                              validate-options]]
            [immutant.web.internal    :refer [default-context mount server]]
            [clojure.walk             :refer [keywordize-keys]])
  (:import [org.projectodd.wunderboss.web Web$CreateOption Web$RegisterOption]))

(defn start
  "Starts a handler with the given options.
   The handler can be a ring handler function, a servlet, or an Undertow
   HttpHandler. Can be called multiple times - if given the same env,
   any prior handler with that env will be replaced. Returns the given
   env with the server object added under :server.
   Needs: options, examples"
  ([handler] (start nil handler))
  ([env handler]
     (let [options (-> env
                     keywordize-keys)]
       (validate-options options start)
       (let [server (server options)]
         (mount server handler options)
         (assoc options :server server)))))

(set-valid-options! start
  (-> (concat
        (opts->set Web$CreateOption)
        (opts->set Web$RegisterOption))
    (conj :server)
    set))

(defn stop
  "Stops a started handler.
   handler-env should be the return value of a start call. If handler-env
   isn't provided, the handler at the root context (\"/\") of the default
   server will be stopped. If there are no handlers remaning on the server,
   the server itself is stopped."
  ([]
     (stop nil))
  ([handler-env]
     (validate-options handler-env start "stop")
     (let [server (server handler-env)
           context-path (:context-path handler-env default-context)]
       (.unregister server context-path)
       (if (empty? (.registeredContexts server))
         (.stop server)))
     nil))
