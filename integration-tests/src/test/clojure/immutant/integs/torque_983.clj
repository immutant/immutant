(ns immutant.integs.torque-983
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [base-url remote]])
  (:require [clj-http.client :as client]
            [immutant.messaging :as msg]))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/ring/torque-983"
                       }))

(deftest should-not-log-error
  (client/get (str (base-url) "/torque_983"))
  (is (= [:web :msg] (remote msg/receive "/queue/results"))))
