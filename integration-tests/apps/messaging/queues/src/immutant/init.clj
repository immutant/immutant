(ns immutant.init
  (:require [immutant.messaging :as msg]))

(msg/start "/queue/ham")
(msg/start ".queue.biscuit")
(msg/listen ".queue.biscuit" #(msg/publish "/queue/ham" (.toUpperCase %)))

(msg/start "/queuebam")
(msg/start "queue/hiscuit")

(msg/start "/queue/loader")
(msg/start "/queue/loader-result")
(msg/listen "/queue/loader"
            (fn [_]
              (msg/publish "/queue/loader-result"
                           (-> (Thread/currentThread)
                               .getContextClassLoader
                               .toString))))
