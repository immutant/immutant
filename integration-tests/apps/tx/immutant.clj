(ns tx.init
  (:use clojure.test)
  (:require [immutant.messaging :as messaging]
            [immutant.web :as web]
            [tx.core]))

(let [results (run-tests 'tx.core)]
  (println results)
  (if (or (> (:error results) 0) (> (:fail results) 0))
    (throw (Exception. "In-container tests failed"))))

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "success"})
(web/start "/" handler)
