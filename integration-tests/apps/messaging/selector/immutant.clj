(ns messaging.init
  (:require [immutant.messaging :as msg]))

(msg/start "/queue/ham")
(msg/start "/queue/filtered" :selector "color = 'blue'")

