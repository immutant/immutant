(ns messaging.init
  (:require [immutant.messaging :as msg]))

(msg/start "/topic/gravy")

(msg/start "topic.biscuit")

(let [p (promise)
      l (msg/listen "topic.biscuit" (fn [v] (deliver p v)))]
  (try
    (msg/publish "topic.biscuit" :success)
    (let [delivery (deref p 1000 :fail)]
      (if-not (= :success delivery)
        (throw (Exception. (str "Should have received :success, but got " delivery)))))
    (finally
     (msg/unlisten l))))

