(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]))

(msg/start "/queue/torque-983")
(msg/start "/queue/results")

(defn msg-handler [msg]
  (msg/publish "/queue/results" (conj msg :msg)))

(msg/listen "/queue/torque-983" msg-handler)

(defn web-handler [request]
  (msg/publish "/queue/torque-983" [:web] :priority 8)
  {:status 200
   :body "nothing"})

(web/start web-handler)