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
  "Serve web requests using Ring handlers, Servlets, or Undertow HttpHandlers"
  (:require [immutant.internal.options :refer [opts->set set-valid-options!
                                               validate-options extract-options]]
            [immutant.internal.util    :refer [kwargs-or-map->map]]
            [immutant.web.internal.wunderboss :refer :all]
            [clojure.walk              :refer [keywordize-keys]])
  (:import [org.projectodd.wunderboss.web Web$CreateOption Web$RegisterOption]))

(defn run
  "Runs `handler` with the given `options`.

   `handler` can be a Ring handler function, a Servlet, or an
   Undertow HttpHandler. Can be called multiple times - if given the
   same options, any prior handler with those options will be replaced. Returns
   the given options with any missing defaults filled in.

   options can be a map or kwargs, with these valid keys [default]:

     * :host          The interface bind address [localhost]
     * :port          The port listening for requests [8080]
     * :path          Maps the handler to a prefix of the url path [/]
     * :virtual-host  Virtual host name[s] (a String or a List of Strings) [nil]

   Run calls may be threaded together, too:

   ```
     (-> (run hello)
       (assoc :path \"/howdy\")
       (->> (run howdy))
       (merge {:path \"/\" :port 8081})
       (->> (run ola)))
   ```

   The above actually creates two web server instances, one listening
   for hello and howdy requests on port 8080, and another listening
   for ola requests on 8081.

   The underlying web server for Immutant is Undertow, which supports
   more advanced options than the above. These can be configured by
   passing an Undertow$Builder instance via the :configuration option,
   and that instance is easily constructed from a Clojure map using
   the {{immutant.web.undertow/options}} function."
  [handler & options]
  (let [options (->> options
                  kwargs-or-map->map
                  keywordize-keys
                  (merge create-defaults register-defaults))]
    (validate-options options run)
    (let [server (server options)]
      (mount server handler options)
      (update-in options [:contexts server] conj (mounts options)))))

(set-valid-options! run (conj (opts->set Web$CreateOption Web$RegisterOption) :contexts))

(defn stop
  "Stops a running handler.

  `options` can be passed as a map or kwargs, but is typically the map
  returned from a {{run}} call. If that return value is not available, you
  can pass the same options map passed to {{run}} for the handler you want
  to stop. If options isn't provided, the handler at the root context
  path (\"/\") of the default server will be stopped. If there are no
  handlers remaining on the server, the server itself is stopped.
  Returns true if a handler was actually removed."
  [& options]
  (let [opts (-> options
               kwargs-or-map->map
               keywordize-keys
               (validate-options run "stop"))
        contexts (:contexts opts {(server opts) [(mounts opts)]})
        stopped (some boolean (doall (for [[s os] contexts, o os]
                                       (.unregister s (extract-options o Web$RegisterOption)))))]
    (doseq [server (keys contexts)]
      (if (empty? (.registeredContexts server))
        (.stop server)))
    stopped))

(defmacro run-dmc
  "Run in Development Mode (the 'C' is silent).

   This macro invokes {{run}} after ensuring the passed handler is
   var-quoted, with reload and stacktrace middleware applied, and then
   opens the app in a browser. Supports the same options as {{run}}."
  [handler & options]
  (let [handler (if (and (symbol? handler)
                      (not (get &env handler))
                      (resolve handler))
                  `(var ~handler)
                  handler)]
    `(run-dmc* run ~handler ~@options)))
