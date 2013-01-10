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
