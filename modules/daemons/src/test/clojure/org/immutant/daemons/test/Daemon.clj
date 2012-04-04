;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns org.immutant.daemons.test.Daemon
  (:use clojure.test
        immutant.test.helpers)
  (:import org.immutant.runtime.ClojureRuntime
           org.immutant.daemons.Daemon))

(def daemon-status (ref "not-started"))

(defn start-daemon []
  (dosync (ref-set daemon-status "started")))

(defn stop-daemon []
  (dosync (ref-set daemon-status "stopped")))

(def ^:dynamic *daemon*)

(use-fixtures :each
              (fn [f]
                (binding [*daemon* (Daemon. start-daemon stop-daemon)]
                  (f))))

(deftest start-should-call-the-start-function
  (.start *daemon*)
  (is (= "started" @daemon-status)))

(deftest stop-should-call-the-stop-function
  (.stop *daemon*)
  (is (= "stopped" @daemon-status)))

(deftest stop-should-not-raise-if-no-stop-function-provided
  (binding [*daemon* (Daemon. start-daemon nil)]
    (is (not-thrown? Exception (.stop *daemon*)))))

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
  (binding [*daemon* (Daemon. start-daemon nil)]
    (.start *daemon*)
    (.stop *daemon*)
    (is-not (.isStopped *daemon*))
    (is (.isStarted *daemon*))
    (is (= "STARTED" (.getStatus *daemon*)))))
