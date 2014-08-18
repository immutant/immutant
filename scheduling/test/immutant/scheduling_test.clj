;; Copyright 2014 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.scheduling-test
  (:require [clojure.test                 :refer :all]
            [immutant.scheduling          :refer :all]
            [immutant.scheduling.internal :refer :all]
            [immutant.util                :as u]))

(use-fixtures :each u/reset-fixture)

(deftest scheduling-should-work
  (let [p (promise)]
    (schedule #(deliver p :success) {})
    (is (= :success (deref p 10000 :failure)))))

(deftest scheduling-should-take-kwargs
  (let [p (promise)]
    (schedule #(deliver p :success) :limit 1 :every 1)
    (is (= :success (deref p 10000 :failure)))))

(deftest should-return-opts-with-the-defaults
  (let [result (schedule #() (in 1))]
    (is (= (-> (merge create-defaults schedule-defaults) keys (conj :id :ids) set)
          (-> result keys set)))
    (is (:id result))
    (is (:ids result))))

(deftest should-return-given-opts-not-overridden-by-defaults
  (let [opts {:id :foo :num-threads 1}
        result (schedule #() opts)]
    (is (= (-> (merge create-defaults schedule-defaults) keys (conj :id :ids) set)
          (-> result keys set)))
    (is (= opts (select-keys result (keys opts))))))

(deftest stop-should-work
    (let [started? (promise)
          should-run? (atom true)
          env (schedule
                (fn []
                  (is @should-run?)
                  (deliver started? true))
                (-> (every 100)
                  (limit 5)))]
      (is (deref started? 5000 false))
      (is (stop env))
      (is (not (stop env)))
      (reset! should-run? false)))

(deftest stop-should-take-kwargs
    (let [started? (promise)
          should-run? (atom true)]
      (schedule
        (fn []
          (is @should-run?)
          (deliver started? true))
        (-> (id :foo)
          (every 1)
          (limit 5)))
      (is (deref started? 5000 false))
      (is (stop :id :foo))
      (reset! should-run? false)))

(deftest stop-should-stop-the-scheduler-when-no-jobs-remain
  (let [job1 (schedule #())
        job2 (schedule #())
        scheduler (.scheduler (scheduler {}))]
    (is (not (.isShutdown scheduler)))
    (stop job1)
    (is (not (.isShutdown scheduler)))
    (stop job2)
    (is (.isShutdown scheduler))))

(deftest stop-should-stop-all-threaded-jobs
  (let [everything (-> (schedule #())
                     (dissoc :id)
                     (->> (schedule #()))
                     (dissoc :id)
                     (->> (schedule #())))
        scheduler (scheduler {})]
    (is (true? (stop everything)))
    (is (empty? (.scheduledJobs scheduler)))
    (is (.isShutdown (.scheduler scheduler)))
    (is (not (stop everything)))))
