(ns jobs.tests
  (:use clojure.test)
  (:require [immutant.jobs :as job]))

(def a-value (atom 0))
(def loader (atom nil))

(defn make-fire-action [p action]
  (fn []
    (println "a-job firing")
    (when (instance? org.quartz.JobExecutionContext job/*job-execution-context*)
      (reset! loader (.toString (.getContextClassLoader (Thread/currentThread))))
      (swap! a-value action))
    (deliver p true)))

(defn wait-for-change [cur d timeout]
  (cond
   (not= cur @d) true
   (>= 0 timeout) false
   :default (do (Thread/sleep 100)
                (recur cur d (- timeout 100)))))

(deftest jobs-should-fire-and-have-the-correct-CL
  (let [p (promise)
        initial-value @a-value]
    (job/schedule "a-job" "*/1 * * * * ?" (make-fire-action p inc))
    (try
      (is (= true (deref p 10000 nil)))
      (let [sample-value @a-value]
        (is (< initial-value sample-value))
        (is (wait-for-change sample-value a-value 1500))
        (is (< sample-value @a-value)))
      (finally (job/unschedule "a-job"))))
  (is (re-find #"ImmutantClassLoader.*deployment\..*\.clj"
               @loader)))

(deftest rescheduling
  (let [p1 (promise)
        initial-value @a-value]
    (job/schedule "a-job" "*/1 * * * * ?" (make-fire-action p1 inc))
    (try
      (is (= true (deref p1 10000 nil)))
      (is (< initial-value @a-value))
      (let [sample-value @a-value
            p2 (promise)]
        (job/schedule "a-job" "*/1 * * * * ?" (make-fire-action p2 dec))
        (is (= true (deref p2 10000 nil)))
        (is (> sample-value @a-value)))
      (finally (job/unschedule "a-job")))))

(deftest unschedule
  (let [p (promise)]
    (job/schedule "a-job" "*/1 * * * * ?" (make-fire-action p inc))
    (is (= true (deref p 10000 nil)))
    (job/unschedule "a-job")
    (is (not (wait-for-change @a-value a-value 5000)))))

