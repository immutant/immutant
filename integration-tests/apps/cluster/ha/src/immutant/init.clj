(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons   :as dmn]
            [immutant.cache     :as csh]
            [immutant.jobs      :as job]
            [clojure.tools.logging :as log]))

(def nodename (System/getProperty "jboss.node.name"))

(msg/start "/queue/cache", :durable false)

(def cache (csh/lookup-or-create "cluster-test", :locking :pessimistic))
(csh/put-if-absent cache :count 0)
  
(defn update-cache []
  (log/info (str "Updating cache: " cache))
  (csh/put cache :node nodename)
  (csh/swap! cache :count inc))

(job/schedule "updater" update-cache :every :second)

(let [resp  (atom nil)
      start #(reset! resp (msg/respond "/queue/cache" (fn [_] (into {} cache))))
      stop  #(msg/unlisten @resp)]
  (dmn/daemonize "cache-status" start stop))
