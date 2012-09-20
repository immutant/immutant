(ns daemons.init
  (:require [immutant.web :as web])
  (:require [immutant.daemons :as daemon]))

(def service (let [x (atom 0)
                   loader (atom nil)
                   done (atom false)]
               {:start (fn []
                         (reset! loader (.getContextClassLoader (Thread/currentThread)))
                         (Thread/sleep 10)
                         (when-not @done
                           (swap! x inc)
                           (recur)))
                :stop (fn []
                        (println "JC: stop x=" @x)
                        (reset! done true))
                :value (fn [] @x)
                :loader (partial deref loader)
                }))

(daemon/run "counter" (:start service) (:stop service))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str {:value ((:value service))
                  :loader (.toString ((:loader service)))})})

(web/start "/" handler)
