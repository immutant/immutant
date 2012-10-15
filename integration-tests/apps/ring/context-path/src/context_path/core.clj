(ns context-path.core)

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (pr-str (dissoc (assoc request
                           :app "context-path")
                         :body))})


