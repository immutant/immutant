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
            [immutant.daemons :as daemon]
            [clojure.tools.logging :as log]))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")
(def oddball-queue (msg/as-queue "oddball"))
(def sleepy-queue "queue.sleeper")

(msg/start ham-queue)
(msg/start biscuit-queue)
(msg/start oddball-queue)
(msg/start sleepy-queue)

(msg/respond ham-queue (memfn toUpperCase))
(msg/respond oddball-queue (memfn toUpperCase))

(msg/respond biscuit-queue (memfn toUpperCase) :selector "worker='upper'")
(msg/respond biscuit-queue (memfn toLowerCase) :selector "worker='lower'")

(msg/respond sleepy-queue (fn [m]
                            (println "SLEEPING" m)
                            (Thread/sleep m)
                            (println "AWAKE")
                            m))
