(ns immutant.init
  (:require [immutant.messaging :as msg]))

(msg/start "/queue/ham")
(msg/start "/queue/filtered" :selector "color = 'blue'")

(msg/listen "/queue/filtered"
            #(msg/publish "/queue/ham" %)
            :selector "animal = 'penguin'")
