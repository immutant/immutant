(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons :as daemon]
            [clojure.tools.logging :as log]))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")

(msg/start ham-queue)
(msg/start biscuit-queue)

(msg/respond ham-queue (memfn toUpperCase))

(msg/respond biscuit-queue (memfn toUpperCase) :selector "worker='upper'")
(msg/respond biscuit-queue (memfn toLowerCase) :selector "worker='lower'")
