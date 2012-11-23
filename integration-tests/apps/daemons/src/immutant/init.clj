(ns immutant.init
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

(def another-service  {:start (fn [] (println "STARTING"))
                       :stop (fn [] (println "STOPPING"))
                       :value (constantly "another-service")
                       :loader (constantly "who cares?")})

(daemon/daemonize "counter" (:start service) (:stop service))

(defn handler [request]
  (let [s (if (re-find #"reload" (or (:query-string request) ""))
            (do
              (daemon/daemonize "counter" (:start another-service) (:stop another-service))
              another-service)
            service)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (pr-str {:value ((:value s))
                    :loader (.toString ((:loader s)))})}))

(web/start "/" handler)
