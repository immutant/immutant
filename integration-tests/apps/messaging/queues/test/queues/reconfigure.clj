(ns queues.reconfigure
  (:use clojure.test)
  (:require [immutant.messaging :as msg]
            [immutant.util :as util]
            [immutant.registry :as reg]
            [clojure.java.io :as io])
  (:import org.jboss.msc.service.ServiceName))

(defn get-from-msc [name]
  (->> name
       ServiceName/parse
       (.getService (deref (deref #'reg/msc-registry)))
       .getService))

(deftest reconfigure-on-a-stopped-queue-should-work
  (msg/stop "queue.reconfigurable")
  (msg/start "queue.reconfigurable" :selector "foo=1")
  (is
   (= "foo=1"
      (-> "jboss.messaging.default.jms.queue.\"queue.reconfigurable\""
          get-from-msc
          .getSelector))))

(deftest reconfigure-on-a-running-queue-should-warn
  (msg/stop "queue.not-reconfigurable")
  (msg/start "queue.not-reconfigurable" :selector "foo=1")
  (is
   (re-find #"WARN.* queue\.not-reconfigurable .*currently active"
            (slurp (io/file (System/getProperty "jboss.server.log.dir")
                            "server.log"))))
  (is
   (= ""
    (-> "jboss.messaging.default.jms.queue.\"queue.not-reconfigurable\""
        get-from-msc
        .getSelector))))
