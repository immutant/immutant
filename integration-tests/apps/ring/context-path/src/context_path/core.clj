(ns context-path.core)

(defn handler [request]
  (let [body "This is context-path"]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body body}))


