(ns daemons.test
  (:use immutant.init
        clojure.test)
  (:require [immutant.daemons :as daemon]))

(deftest simple "it should work"
  (is (< 0 ((:value service)))))

(deftest the-daemon-should-be-using-the-deployment-classloader
  (is (re-find #"ImmutantClassLoader.*deployment\..*\.clj" (str ((:loader service))))))

(deftest daemons-should-be-reloadable
  ;; Overwrite service
  (daemon/daemonize "counter" (:start another-service) (:stop another-service))

  ;; This is silly, because it has nothing to do with daemonization.
  ;; TODO: Retrieve the service via some daemon api
  (is (= "another-service" ((:value another-service))))

  ;; Now verify that the old service is stopped
  (Thread/sleep 11)
  (let [v ((:value service))]
    (Thread/sleep 22)
    (is (= v ((:value service))))))
