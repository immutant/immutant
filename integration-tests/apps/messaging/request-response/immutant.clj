(ns messaging.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons :as daemon]
            [clojure.tools.logging :as log]))

(def ham-queue "/queue/ham")

(msg/start ham-queue)

(msg/respond ham-queue (memfn toUpperCase))
