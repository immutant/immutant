(ns init-from-ring.core)

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (pr-str body)})

(defn handler [request]
  (response "init-from-ring"))

