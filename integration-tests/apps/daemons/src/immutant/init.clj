(ns immutant.init
  (:require [immutant.daemons :as daemon]))

(def service (let [x (atom 0)
                   loader (atom nil)
                   done (atom false)
                   callback (atom nil)]
               {:start (fn []
                         (reset! loader (.getContextClassLoader (Thread/currentThread)))
                         (Thread/sleep 10)
                         (when-not @done
                           (swap! x inc)
                           (recur)))
                :stop (fn []
                        (reset! done true)
                        (and @callback (@callback)))
                :value (fn [] @x)
                :loader (partial deref loader)
                :callback (fn [f] (reset! callback f))
                }))

(def another-service  {:start (fn [] (println "STARTING"))
                       :stop (fn [] (println "STOPPING"))
                       :value (constantly "another-service")
                       :loader (constantly "who cares?")})

(daemon/daemonize "counter" (:start service) (:stop service))
