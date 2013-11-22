(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.daemons   :as dmn]))

(msg/start "/queue/cluster" :durable false)
(msg/start "/queue/nodename" :durable false)

(defn nodename [_]
  (System/getProperty "jboss.node.name"))

(let [resp  (atom nil)
      start (fn [] (reset! resp (msg/respond "/queue/nodename" nodename)))
      stop  (fn [] (msg/unlisten @resp))]
  (dmn/daemonize "upcaser" start stop))

