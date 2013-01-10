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

(msg/start "queue.listen-id.request")
(msg/start "queue.listen-id.response")

(msg/listen "queue.listen-id.request"
            (fn [_]
              (msg/listen "queue.listen-id.request"
               (fn [_] (msg/publish "queue.listen-id.response" :new-listener)))
              (msg/publish "queue.listen-id.response" :old-listener)))

(msg/start (msg/as-queue "oddball"))
(msg/start (msg/as-queue "addboll"))
(msg/start (msg/as-queue "odd-response"))

(msg/listen (msg/as-queue "oddball")
            #(msg/publish "/queue/ham" (.toLowerCase %)))




