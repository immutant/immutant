(ns org.fnbox.daemons.test.Daemon
  (:use clojure.test)
  (:use fnbox.test.helpers)
  (:import [org.fnbox.core ClojureRuntime])
  (:import [org.fnbox.daemons Daemon]))

(def daemon-status (ref "not-started"))

(defn start-daemon []
  (dosync (ref-set daemon-status "started")))

(defn stop-daemon []
  (dosync (ref-set daemon-status "stopped")))

(def ^:dynamic *daemon*)

(use-fixtures :each
              (fn [f]
                (binding [*daemon* (Daemon. (ClojureRuntime. (.getClassLoader Daemon))
                                            "org.fnbox.daemons.test.Daemon/start-daemon"
                                            "org.fnbox.daemons.test.Daemon/stop-daemon")]
                  (f))))

(deftest start-should-call-the-start-function
  (.start *daemon*)
  (is (= "started" @daemon-status)))

(deftest stop-should-call-the-stop-function
  (.stop *daemon*)
  (is (= "stopped" @daemon-status)))

(deftest stop-should-not-raise-if-no-stop-function-provided
  (.setStopFunction *daemon* nil)
  (is (not-thrown? Exception  (.stop *daemon*))))

(deftest it-should-be-started-after-start
  (.start *daemon*)
  (is (.isStarted *daemon*))
  (is-not (.isStopped *daemon*))
  (is (= "STARTED" (.getStatus *daemon*))))

(deftest it-should-be-stopped-after-stop
  (.start *daemon*)
  (.stop *daemon*)
  (is (.isStopped *daemon*))
  (is-not (.isStarted *daemon*))
  (is (= "STOPPED" (.getStatus *daemon*))))

(deftest it-should-not-be-stopped-after-stop-if-no-stop-function-provided
  (.setStopFunction *daemon* nil)
  (.start *daemon*)
  (.stop *daemon*)
  (is-not (.isStopped *daemon*))
  (is (.isStarted *daemon*))
  (is (= "STARTED" (.getStatus *daemon*))))
