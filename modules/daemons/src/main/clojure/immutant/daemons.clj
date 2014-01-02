;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.daemons
  "Asynchronous, highly-available services that share the lifecycle of
   your application"
  (:require [immutant.registry :as registry]))

(defprotocol Daemon
  "Functions for controlling a long-running service"
  (start [daemon]
    "Start the service")
  (stop [daemon]
    "Stop the service"))

(defn create
  "Start a daemon asynchronously, creating an MBean named by name,
   invoking the stop function automatically at undeployment/shutdown.
   If :singleton is truthy, the service will start on only one node in
   a cluster"
  [name daemon & {singleton :singleton :or {singleton true}}]
  (if-let [daemonizer (registry/get "daemonizer")]
    (.createDaemon daemonizer (clojure.core/name name) #(start daemon) #(stop daemon) (boolean singleton))))

(defn daemonize
  "Convenience function for creating a daemon from a name and
  start/stop functions"
  [name start-fn stop-fn & opts]
  (let [daemon (reify Daemon
                 (start [_] (start-fn))
                 (stop [_] (stop-fn)))]
    (apply create name daemon opts)))
