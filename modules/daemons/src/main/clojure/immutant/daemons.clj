;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
  "Asynchronous services that share the lifecycle of your application"
  (:require [immutant.registry :as lookup]))

(defn start 
  "Start a service asynchronously, creating an MBean named by name,
   invoking the stop function automatically at undeployment/shutdown.
   If :singleton is truthy, the service will start on only one node
   in a cluster"
  [name start stop & {singleton :singleton :or {singleton true}}]
  (if-let [^org.immutant.daemons.Daemonizer daemonizer (lookup/fetch "daemonizer")]
    (.createDaemon daemonizer name #(future (start)) stop (boolean singleton))))
  
