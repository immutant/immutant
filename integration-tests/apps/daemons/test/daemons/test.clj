(ns daemons.test
  (:use immutant.init
        clojure.test)
  (:require [immutant.daemons :as daemon]))

(deftest simple "it should work"
  (is (< 0 ((:value service)))))

(deftest the-daemon-should-be-using-the-deployment-classloader
  (is (re-find #"ImmutantClassLoader.*deployment\..*\.clj" (str ((:loader service))))))

(deftest daemons-should-be-reloadable
  (let [p (promise)]
    ((:callback service) (fn [] (deliver p :success)))
    (daemon/daemonize "counter" (:start another-service) (:stop another-service))
    (is (= :success (deref p 30000 :failure))))

  ;; This is silly, because it has nothing to do with daemonization.
  ;; TODO: Retrieve the service by name via some daemon api
  (is (= "another-service" ((:value another-service)))))
