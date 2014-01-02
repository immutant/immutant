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

(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons   :as dmn]
            [immutant.cache     :as csh]
            [immutant.jobs      :as job]
            [clojure.tools.logging :as log]))

(def nodename (System/getProperty "jboss.node.name"))

(msg/start "/queue/cache", :durable false)

(def cache (csh/lookup-or-create "cluster-test", :locking :pessimistic))
(csh/put-if-absent cache :count 0)
  
(defn update-cache []
  (log/info (str "Updating cache: " cache))
  (csh/put cache :node nodename)
  (csh/swap! cache :count inc))

(job/schedule "updater" update-cache :every :second)

(let [resp  (atom nil)
      start #(reset! resp (msg/respond "/queue/cache" (fn [_] (into {} cache))))
      stop  #(msg/unlisten @resp)]
  (dmn/daemonize "cache-status" start stop))
