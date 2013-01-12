(ns immutant.integs.torque-983
  (:use fntest.core
        clojure.test)
  (:require [clj-http.client :as client]
            [immutant.messaging :as msg]))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/ring/torque-983"
                       }))

(deftest should-not-log-error
  (client/get "http://localhost:8080/torque_983")
  (is (= [:web :msg] (msg/receive "/queue/results"))))