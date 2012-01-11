(ns messaging.concurrency.init
  (:require [immutant.messaging :as msg]))

(defn handler [msg]
  (msg/publish "/queue/backchannel" (.hashCode (Thread/currentThread))))

(msg/start "/queue/main")
(msg/start "/queue/backchannel")
(msg/listen "/queue/main" handler :concurrency 1)

