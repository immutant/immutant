(ns immutant.init
  (:require [immutant.messaging :as msg]))

;; used by the properties test
(msg/start "/queue/properties")

(msg/start "/queue/selectors")
(msg/start "/queue/filtered" :selector "color = 'blue'")

(msg/listen "/queue/filtered"
            #(msg/publish "/queue/selectors" %)
            :selector "animal = 'penguin'")
