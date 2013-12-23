(ns immutant.init
  (:require [immutant.messaging :as msg]))

(msg/start "/queue/cluster", :durable false)
