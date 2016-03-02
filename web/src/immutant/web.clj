;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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
  (:require [immutant.internal.options :refer [boolify opts->set set-valid-options!
                                               validate-options extract-options]]
            [immutant.internal.util    :refer [kwargs-or-map->map try-resolve]]
            [immutant.web.internal.wunderboss :as wboss])
  (:import [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]))

(defn run
  "Runs `handler` with the given `options`.

   `handler` can be a Ring handler function, a Servlet, or an
   Undertow HttpHandler. Can be called multiple times - if given the
   same options, any prior handler with those options will be replaced. Returns
   the given options with any missing defaults filled in.

   options can be a map or kwargs, with these valid keys [default]:

     * :host          The interface bind address [\"localhost\"]
     * :port          The port listening for requests [8080]
     * :path          Maps the handler to a prefix of the url path [\"/\"]
     * :virtual-host  Virtual host name[s] (a String or a List of Strings) [nil]
     * :dispatch?     Invoke handlers in worker thread pool [true]

  When `handler` is a Servlet, the following options are also supported:
  
     * :servlet-name  The servlet's registered name [the :path]
     * :filter-map    An insertion-order-preserving mapping, e.g. array-map
                      or LinkedHashMap, of names to Filter instances [nil]
  
   Note the web server only binds to the loopback interface, by
   default. To expose your handler to the network, set :host to an
   external IP address, or use \"0.0.0.0\" to bind it to all interfaces.

   The :virtual-host option enables name-based virtual hosting which,
   along with the :path option, distinguishes the handlers on a single
   server. That is, multiple handlers can run on the same `:host` and
   `:port` as long as each has a unique combination of `:virtual-host`
   and `:path`.

   Run calls may be threaded together:

   ```
     (-> (run ello)
       (assoc :path \"/owdy\")
       (->> (run owdy))
       (merge {:path \"/\" :port 8081})
       (->> (run ola)))
   ```

   The above actually creates two web server instances, one listening
   for ello and owdy requests on port 8080, and another listening
   for ola requests on 8081.

   The underlying web server for Immutant is Undertow, which supports
   more advanced options than the above. These can be configured by
   passing an `Undertow$Builder` via the :configuration option, an
   instance of which is easily constructed from a map of valid
   keywords using the [[immutant.web.undertow/options]] function. For
   convenience, all of its option keywords are valid for `run`, too:
   if present, an `Undertow$Builder` will be returned in the result.

   If your handlers are compute-bound, you may be able to gain some
   performance by setting :dispatch? to false. This causes the
   handlers to run on Undertow's I/O threads, avoiding the context
   switch of dispatching them to the worker thread pool, at the
   risk of refusing client requests under load. Note that when
   :dispatch? is false, you cannot use an InputStream or File as
   a ring :body for performance reasons.

   Inside WildFly, the :host, :port, :configuration, and :dispatch?
   options are ignored, since all handlers are mounted as servlets
   contained within WildFly's own Undertow instance. Further, all
   invocations of `run` must be within the initialization function for
   your application, i.e. your `-main`."
  [handler & options]
  (let [undertow-options-maybe (try-resolve 'immutant.web.undertow/options-maybe)
        options (-> options
                  kwargs-or-map->map
                  (validate-options run)
                  wboss/available-port
                  (->> (merge wboss/create-defaults wboss/register-defaults))
                  (cond-> undertow-options-maybe undertow-options-maybe))]
    (let [server (wboss/server options)]
      (wboss/mount server handler options)
      (update-in options [:contexts server] conj (wboss/mounts options)))))

(set-valid-options! run (-> (opts->set Web$CreateOption Web$RegisterOption)
                          (conj :contexts)
                          (boolify :dispatch)
                          (clojure.set/union (-> (try-resolve 'immutant.web.undertow/options)
                                               meta
                                               :valid-options))))

(defn stop
  "Stops a running handler.

  `options` can be passed as a map or kwargs, but is typically the map
  returned from a [[run]] call. If that return value is not available, you
  can pass the same options map passed to [[run]] for the handler you want
  to stop. If options isn't provided, the handler at the root context
  path (\"/\") of the default server will be stopped. If there are no
  handlers remaining on the server, the server itself is stopped.
  Returns true if a handler was actually removed."
  [& options]
  (let [opts (-> options
               kwargs-or-map->map
               (validate-options run "stop"))
        contexts (:contexts opts {(wboss/server opts) [(wboss/mounts opts)]})
        stopped (some boolean (doall (for [[^Web s os] contexts, o os]
                                       (.unregister s (extract-options o Web$RegisterOption)))))]
    (doseq [^Web server (keys contexts)]
      (if (empty? (.registeredContexts server))
        (.stop server)))
    stopped))

(defmacro run-dmc
  "Run in Development Mode (the 'C' is silent).

   This macro invokes [[run]] after ensuring the passed handler is
   var-quoted, with reload and stacktrace middleware applied, and then
   opens the app in a browser. Supports the same options as [[run]]."
  [handler & options]
  (let [handler (if (and (symbol? handler)
                      (not (get &env handler))
                      (resolve handler))
                  `(var ~handler)
                  handler)]
    `(wboss/run-dmc* run ~handler ~@options)))

(defn server
  "Returns the web server instance associated with a particular set of
   options, typically the map returned from a [[run]] call. The web
   server provides `start`, `stop` and `isRunning` methods, allowing
   you to, for example, temporarily stop serving requests for all the
   handlers running on a particular server.

   ```
     (let [srv (server (run hello :auto-start false))]
       (.isRunning srv)   ;=> false
       (.start srv)
       (.isRunning srv)   ;=> true
       (.stop srv))
   ```

   The return value is either a single server instance or a list of
   servers if passed the result from threaded run calls that would
   cause multiple servers to be created."
  [& options]
  (let [options (->> options
                  kwargs-or-map->map
                  (merge wboss/create-defaults wboss/register-defaults))
        servers (keys (:contexts options {(wboss/server options) nil}))]
    (if (= 1 (count servers))
      (first servers)
      servers)))
