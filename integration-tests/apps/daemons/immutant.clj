(ns daemons.init
  (:require [immutant.daemons :as bobaloo]))

(def service (let [x (atom 0)
                   done (atom false)]
               {
                :start (fn []
                         (println "JC: start x=" @x)
                         (Thread/sleep 100)
                         (when-not @done
                           (swap! x inc)
                           (recur)))
                :stop (fn []
                        (println "JC: stop x=" @x)
                        (reset! done true))
                :value (fn [] @x)
                }))

(bobaloo/start "counter" (:start service) (:stop service))
