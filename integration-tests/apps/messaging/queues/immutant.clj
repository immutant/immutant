(ns messaging.init
  (:require [immutant.messaging :as msg]))

(msg/start "/queue/ham")
(msg/start "/queue/biscuit")
(msg/listen "/queue/biscuit" #(msg/publish "/queue/ham" (.toUpperCase %)))

(msg/start "/queue/bam")
(msg/start "/queue/hiscuit")

