;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns test.immutant.jobs
  (:use immutant.jobs
        immutant.jobs.internal
        [immutant.util :only [at-exit]]
        immutant.test.helpers
        clojure.test
        midje.sweet)
  (:require [immutant.registry :as registry])
  (:import org.immutant.jobs.JobSchedulizer
           org.projectodd.polyglot.jobs.BaseJob
           java.util.Date))

(defn fun [])

(deffact "schedule unschedules and creates a job"
  (schedule "name" "spec" fun) => anything
  (provided
    (unschedule "name") => nil
    (create-job (as-checker fn?) "name" "spec" true) => :job))

(def job-args (atom nil))

(registry/put "job-schedulizer"
              (proxy [JobSchedulizer] [nil]
                (createScheduler [_])
                (createJob [& args]
                  (reset! job-args
                          (zipmap [:handler :name :cron-ex :singleton?] args))
                  nil)
                (createAtJob [& args]
                  (reset! job-args
                          (zipmap [:handler :name :start-at :end-at :every :repeat :singleton?]
                                  args))
                  nil)))

(deftest scheduling-at-jobs
  (with-redefs [unschedule (fn [_])]
    
    (testing "it should raise if given :at and :in"
      (is (thrown? IllegalArgumentException (schedule "name" {:at 0 :in 0} fun))))

    (testing "it should raise if given :until without :every"
      (is (thrown? IllegalArgumentException (schedule "name" {:until 5} fun))))

    (testing "it should raise if given :repeat without :every"
      (is (thrown? IllegalArgumentException (schedule "name" {:repeat 5} fun))))
    
    (testing "it should turn :in into a date"
      (let [d (Date. (+ 5000 (System/currentTimeMillis)))]
        (with-redefs [date (fn [_] d)]
          (schedule "name" {:in 5000} fun)
          (is (= d (:start-at @job-args))))))
    
    (testing "it should turn a long :at into a date"
      (let [at (System/currentTimeMillis)]
        (schedule "name" {:at at} fun)
        (is (= (Date. at) (:start-at @job-args)))))

    (testing "it should pass through a Date :at"
      (let [at (Date.)]
        (schedule "name" {:at at} fun)
        (is (= at (:start-at @job-args)))))

    (testing "it should turn a long :until into a date"
      (let [until (System/currentTimeMillis)]
        (schedule "name" {:until until :every 5} fun)
        (is (= (Date. until) (:end-at @job-args)))))

    (testing "it should pass through a Date :until"
      (let [until (Date.)]
        (schedule "name" {:until until :every 5} fun)
        (is (= until (:end-at @job-args)))))

    (testing "it should pass through a non-nil :every"
      (schedule "name" {:every 5} fun)
      (is (= 5 (:every @job-args))))
    
    (testing "it should pass through a non-nil :repeat"
      (schedule "name" {:repeat 5 :every 5} fun)
      (is (= 5 (:repeat @job-args))))
    
    (testing "it should treat a nil :every as 0"
      (schedule "name" {:every nil} fun)
      (is (= 0 (:every @job-args))))
    
    (testing "it should treat a nil :repeat as 0"
      (schedule "name" {:repeat nil} fun)
      (is (= 0 (:repeat @job-args))))))
