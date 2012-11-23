(ns immutant.init
  (:require [immutant.messaging :as msg]))

(defn handler [m]
  (if (= m "start")
    (dotimes [x 100] (msg/publish "/queue/main" x))
    (msg/publish "/queue/backchannel" (.hashCode (Thread/currentThread)))))

(msg/start "/queue/main")
(msg/start "/queue/backchannel")
(msg/listen "/queue/main" handler :concurrency 2)



