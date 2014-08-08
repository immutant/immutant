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
            [immutant.internal.options :as o]
            [wunderboss.util        :as wu])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.singleton
            SingletonContext
            SingletonContext$CreateOption]))

(defn reset
  "Resets the underlying WunderBoss layer.
   This stops and clears all services. Intended to be used from a repl."
  []
  (WunderBoss/shutdownAndReset))

(defn in-container?
  "Returns true if running inside a WildFly/EAP container."
  []
  (wu/in-container?))

(defn app-root
  "Returns a file pointing to the root dir of the application."
  []
  (io/file (get (WunderBoss/options) "root")))

(defn app-name
  "Returns the name of the current application."
  []
  (get (WunderBoss/options) "deployment-name" ""))

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

(defn singleton-daemon
  "Sets up a highly-available singleton daemon.

   When a singleton daemon is started, a new thread is spawned, and
   `start-fn` is invoked. When the application is shut down, `stop-fn` is
   called.

   In a WildFly cluster, daemons with the same `daemon-name` can
   be created on each node, but only one of those daemons will run at
   a time. If the currently running daemon dies (or the node it is on
   loses connection with the cluster), the daemon will automatically
   start on one of the other nodes where it has been created.

   If used outside of WildFly, or in a WildFly instance not in a cluster,
   it behaves as if the cluster size is 1, and starts immediatey."
  [daemon-name start-fn stop-fn]
  (doto
      (WunderBoss/findOrCreateComponent SingletonContext
        (name daemon-name)
        (o/extract-options {:daemon true, :daemon-stop-callback stop-fn}
          SingletonContext$CreateOption))
    (.setRunnable start-fn)
    .start))
