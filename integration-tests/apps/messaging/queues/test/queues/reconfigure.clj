(ns queues.reconfigure
  (:use clojure.test)
  (:require [immutant.messaging :as msg]
            [immutant.util :as util]
            [immutant.registry :as reg])
  (:import org.jboss.msc.service.ServiceName))

(defn get-from-msc [name]
  (.getService (deref (deref #'reg/msc-registry)) (ServiceName/parse name)))

(deftest reconfigure-on-a-stopped-queue-should-work
  (msg/stop "queue.reconfigurable")
  (msg/start "queue.reconfigurable" :selector "foo=1")
  (let [service (-> "jboss.messaging.default.jms.queue.\"queue.reconfigurable\""
                    get-from-msc
                    .getService)]
    (is (= "foo=1" (.getSelector service)))))

(deftest reconfigure-on-a-running-queue-should-throw
  (msg/stop "queue.not-reconfigurable")
  (is (thrown? IllegalStateException
               (msg/start "queue.not-reconfigurable" :selector "foo=1"))))
