(ns daemons.init
  (:require [immutant.web :as web])
  (:require [immutant.daemons :as daemon]))

(def service (let [x (atom 0)
                   done (atom false)]
               {:start (fn []
                         (Thread/sleep 10)
                         (when-not @done
                           (swap! x inc)
                           (recur)))
                :stop (fn []
                        (println "JC: stop x=" @x)
                        (reset! done true))
                :value (fn [] @x)
                }))

(daemon/start "counter" (:start service) (:stop service))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (str ((:value service)))})

(web/start "/" handler)
