(ns immutant.init
  (:require [immutant.messaging :as msg]))

(def q "/queue/cluster")
(msg/start q :durable false)
(msg/respond q (memfn toUpperCase))
