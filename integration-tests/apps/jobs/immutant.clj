(ns jobs.init
  (:require [immutant.web :as web])
  (:require [immutant.jobs :as job]))


(def a-value (atom 0))
(def another-value (atom 0))

(job/schedule "a-job" "*/1 * * * * ?" (fn []
                                        (println "a-job firing")
                                        (swap! a-value inc)))
(job/schedule "another-job" "*/1 * * * * ?" (fn []
                                              (println "another-job firing")
                                              (swap! another-value inc)))

(defn handler [request]
  (if (re-find #"reschedule" (:query-string request))
    (job/schedule "another-job" "*/1 * * * * ?" #(reset! another-value "rescheduled")))
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (with-out-str (pr {:a-value @a-value :another-value @another-value}))})

(web/start "/" handler)






