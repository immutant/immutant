(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.web       :as web]))

(msg/start "/topic/gravy")
(msg/start (msg/as-topic "toddball"))

(let [p (promise)
      l (msg/listen "/topic/gravy" (fn [v] (deliver p v)))]
  (try
    (msg/publish "/topic/gravy" :success)
    (let [delivery (deref p 1000 :fail)]
      (if-not (= :success delivery)
        (throw (Exception. (str "Should have received :success, but got " delivery)))))
    (finally
     (msg/unlisten l))))

(msg/start "queue.198")
(msg/start "topic.198")

;;; Topic listeners are additive, not idempotent
@(msg/listen "topic.198" #(msg/publish "queue.198" (inc %)))
@(msg/listen "topic.198" #(msg/publish "queue.198" (dec %)))

(msg/start "queue.result")
(msg/start "topic.echo")

(let [responder (atom nil)]
  (web/start
   (fn [request]
     (if @responder
       (do
         (msg/unlisten @responder)
         (reset! responder nil))
       (reset! responder @(msg/listen "topic.echo" #(msg/publish "queue.result"
                                                                 (identity %)))))
     {:status 200
      :body ":success"})))
