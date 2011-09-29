(ns basic-ring.core)

(def shared? "app1")

(defn handler [request]
  (let [body (str "Hello From Clojure inside TorqueBox! This is basic-ring<pre>" shared? "</pre><img src='biscuit.jpg'/>")]
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body body}))
