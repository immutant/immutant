(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.web       :as web]
            [immutant.util      :as util]))

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

(msg/start "queue.listen-id.request" :durable false)
(msg/start "queue.listen-id.response" :durable false)

(msg/listen "queue.listen-id.request"
            (fn [_]
              (future
                (msg/listen "queue.listen-id.request"
                            (fn [_] (msg/publish "queue.listen-id.response" :new-listener)))
                (msg/publish "queue.listen-id.response" :release))
              (msg/publish "queue.listen-id.response" :old-listener)))

(msg/start (msg/as-queue "oddball"))
(msg/start (msg/as-queue "addboll"))
(msg/start (msg/as-queue "odd-response"))

(msg/listen (msg/as-queue "oddball")
            #(msg/publish "/queue/ham" (.toLowerCase %)))

(msg/start "queue.echo")

(let [responder (atom nil)]
  (web/start
   (fn [request]
     (if @responder
       (do
         (msg/unlisten @responder)
         (reset! responder nil))
       (reset! responder (msg/respond "queue.echo" identity)))
     {:status 200
      :body ":success"})))

(msg/start "queue.reconfigurable")
(msg/start "queue.not-reconfigurable")
(when (re-find #"^/q\d" (util/context-path))
  (Thread/sleep 2000)
  (msg/stop "queue.reconfigurable"))
