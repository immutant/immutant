(ns daemons.test
  (:use clojure.test)
  (:require [immutant.daemons :as daemon]
            [immutant.util    :as util]
            [clojure.java.jmx :as jmx]))

(deftest daemon-started-async
  (let [started (promise)
        thread (atom nil)]
    (letfn [(start []
              (reset! thread (Thread/currentThread))
              (deliver started :success))]
      (daemon/daemonize "async" start #())
      (is (= :success (deref started 30000 :failure)))
      (is (not= @thread (Thread/currentThread))))))

(deftest daemon-using-the-deployment-classloader
  (let [started (promise)
        loader (atom nil)]
    (letfn [(start []
              (reset! loader (.getContextClassLoader (Thread/currentThread)))
              (deliver started :success))]
      (daemon/daemonize "loader" start #())
      (is (= :success (deref started 30000 :failure)))
      (is (re-find #"ImmutantClassLoader.*deployment\..*\.clj" (str @loader))))))

(deftest daemon-replace-calls-stop
  (let [started (promise)
        stopped (promise)]
    (letfn [(start [] (deliver started :success))
            (stop [] (deliver stopped :success))]
      (daemon/daemonize :reload start stop)
      (is (= :success (deref started 30000 :failure)))
      (daemon/daemonize "reload" #() #())
      (is (= :success (deref stopped 30000 :failure))))))

(deftest daemon-should-have-mbean
  (daemon/daemonize :mbean #() #())
  (is (util/backoff 1 10000
        (jmx/mbean "immutant.daemons:name=mbean,app=daemons"))))
