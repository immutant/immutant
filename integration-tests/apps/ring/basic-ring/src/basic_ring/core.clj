(ns basic-ring.core
  (:require [immutant.utilities :as util]))

(def shared? "app1")

(defn handler [request]
  (let [body (str "Hello From Clojure inside TorqueBox! This is basic-ring<pre>" shared? "</pre><img src='biscuit.jpg'/>")]
    (println "JC: registry=" util/registry)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))
