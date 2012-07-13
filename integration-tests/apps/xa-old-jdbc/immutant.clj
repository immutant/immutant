(ns xa.init
  (:require [immutant.xa :as xa]
            [immutant.web :as web]))

;;; A web interface
(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "success"})
(web/start "/thing" handler)