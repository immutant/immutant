(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]))

(msg/start "/queue/test")
(msg/start "/queue/results")

(defn msg-handler [msg]
  (msg/publish "/queue/results" (conj msg :msg) :priority 1))

(msg/listen "/queue/test" msg-handler)

(defn web-handler [request]
  (msg/publish "/queue/test" [:web] :priority 8)
  {:status 200
   :body "nothing"})

(web/start web-handler)