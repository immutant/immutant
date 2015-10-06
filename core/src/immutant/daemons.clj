;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.daemons
  "Asynchronous, highly-available services that share the lifecycle of
   your application."
  (:require [immutant.internal.options :as o])
  (:import org.projectodd.wunderboss.WunderBoss
           [org.projectodd.wunderboss.ec
            DaemonContext
            DaemonContext$CreateOption
            DaemonContext$StopCallback]))

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
  (doto ^DaemonContext
    (WunderBoss/findOrCreateComponent DaemonContext
      (name daemon-name)
      ;; TODO: expose :stop-timeout
      (o/extract-options {:singleon true}
            DaemonContext$CreateOption))
    (.setAction start-fn)
    (.setStopCallback (reify DaemonContext$StopCallback
                        (notify [_ _]
                          (stop-fn))))
    .start))
