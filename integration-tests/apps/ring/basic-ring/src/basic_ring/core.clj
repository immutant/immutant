(ns basic-ring.core
  (:use immutant.messaging)
  (:require [immutant.registry :as service]))

(def shared? "app1")

(defn handler [request]
  (if (.endsWith (:uri request) "process")
    (processor "/queue/biscuit" #(publish "/queue/ham" (.toUpperCase %))))
  (let [body (str "Hello From Clojure inside TorqueBox! This is basic-ring<pre>" shared? "</pre><img src='biscuit.jpg'/>")
        factory (service/service "jboss.naming.context.java.ConnectionFactory")]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str body "<p>" factory "</p>")}))
