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

(ns immutant.util
  "Various utility functions."
  (:require [clojure.string         :as str]
            [clojure.java.io        :as io]
            [clojure.java.classpath :as cp]
            [immutant.internal.util :refer [try-resolve]]
            [wunderboss.util        :as wu])
  (:import org.projectodd.wunderboss.WunderBoss))

(defn reset
  "Resets the underlying WunderBoss layer.
   This stops and clears all services. Intended to be used from a repl or from tests."
  []
  (WunderBoss/shutdownAndReset))

(defn in-container?
  "Returns true if running inside a WildFly/EAP container."
  []
  (wu/in-container?))

(defn in-cluster?
  "Returns true if running inside a WildFly/EAP container that's part of a cluster"
  []
  (if-let [f (try-resolve 'immutant.wildfly/in-cluster?)]
    (f)))

(defn reset-fixture
  "Invokes `f`, then calls {{reset}} if not {{in-container?}}.

   Useful as a test fixture where you want to reset underlying state
   after a test run, but also run the same tests in-container (via fntest
   or other), where resetting state will disconnect the repl. In the
   in-container case, you rely on undeploy to reset the state."
  [f]
  (try
    (f)
    (finally
      (when-not (in-container?)
        (reset)))))

(defn app-root
  "Returns a file pointing to the root dir of the application."
  []
  (io/file (get (WunderBoss/options) "root")))

(defn app-name
  "Returns the name of the current application."
  []
  (get (WunderBoss/options) "deployment-name" ""))

(defn ^:internal ^:no-doc in-container-port
  "Returns the (possibly offset) port from the socket-binding in standalone.xml"
  [socket-binding-name]
  (when (in-container?)
    (when-let [sb ((try-resolve 'immutant.wildfly/get-from-service-registry)
                   (str "jboss.binding." (name socket-binding-name)))]
      (.getAbsolutePort sb))))

(defn http-port
  "Returns the HTTP port for the embedded web server.

   Returns the correct port when in-container, and the default (8080),
   outside."
  []
  (or (in-container-port :http) 8080))

(defn messaging-remoting-port
  "Returns the port that HornetQ is listening on for remote connections.

   Returns the correct port when in-container, and the default (5445),
   outside."
  []
  (or (in-container-port :http) 5445))

(defn app-relative
  "Returns an absolute file relative to {{app-root}}."
  [& path]
  (if-let [root (app-root)]
    (apply io/file root path)
    (apply io/file path)))

(defn classpath
  "Returns the effective classpath for the application."
  []
  (cp/classpath))

(defn dev-mode?
  "Returns true if the app is running in dev mode.

   This is controlled by the `LEIN_NO_DEV` environment variable."
  []
  (not (System/getenv "LEIN_NO_DEV")))

(defn at-exit
  "Registers `f` to be called when the application is undeployed.
   Used internally to shutdown various services, but can be used by
   application code as well."
  [f]
  (wu/at-exit f))

(defn set-bean-property
  "Calls a java bean-style setter (.setFooBar) for the given property (:foo-bar) and value."
  [bean prop value]
  (let [setter (->> (str/split (name prop) #"-")
                 (map str/capitalize)
                 (apply str ".set")
                 symbol)]
    ((eval `#(~setter %1 %2)) bean value)))

(defn set-log-level!
  "Sets the global log level for the interal logging system.

   Valid options for `level` are: :OFF, :ERROR, :WARN, :INFO, :DEBUG, :TRACE, :ALL"
  [level]
  (WunderBoss/setLogLevel (name level)))
