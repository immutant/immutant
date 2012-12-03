(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons :as daemon]
            [clojure.tools.logging :as log]))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")
(def oddball-queue (msg/as-queue "oddball"))

(msg/start ham-queue)
(msg/start biscuit-queue)
(msg/start oddball-queue)

(msg/respond ham-queue (memfn toUpperCase))
(msg/respond oddball-queue (memfn toUpperCase))

(msg/respond biscuit-queue (memfn toUpperCase) :selector "worker='upper'")
(msg/respond biscuit-queue (memfn toLowerCase) :selector "worker='lower'")
