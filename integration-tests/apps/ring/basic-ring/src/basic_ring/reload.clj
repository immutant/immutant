(ns basic-ring.reload
  (:require basic-ring.spittle))

(defn reload-test-handler
  [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (basic-ring.spittle/content)})
