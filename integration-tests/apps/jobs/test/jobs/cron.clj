(ns jobs.cron
  (:use clojure.test
        jobs.helper)
  (:require [immutant.jobs      :as job]
            [immutant.messaging :as msg]
            [immutant.util      :as util]
            [clojure.java.jmx   :as jmx])
  (:import java.util.concurrent.atomic.AtomicInteger))

(defmacro with-job [action & body]
  `(try
     (job/schedule "a-job" ~action "*/1 * * * * ?")
     ~@body
      (finally (job/unschedule "a-job"))))

(deftest jobs-should-work
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping")
      (is (= ["ping" "ping" "ping"] (take 3 (msg/message-seq q)))))))

(deftest jobs-should-have-mbeans
  (with-job #()
    (is (util/backoff 1 10000
          (jmx/mbean "immutant.jobs:name=a-job,app=jobs")))))

(deftest jobs-should-work-with-a-keyword-name
  (let [q (random-queue)]
    (try
      (job/schedule :kw-job #(msg/publish q "ping") "*/1 * * * * ?")
      (is (= ["ping" "ping" "ping"] (take 3 (msg/message-seq q))))
     (finally (job/unschedule :kw-job)))))

(deftest rescheduling
  (let [q1 (random-queue)
        q2 (random-queue)]
    (with-job #(msg/publish q1 "ping")
      (is (= ["ping" "ping"] (take 2 (msg/message-seq q1))))
      (with-job #(msg/publish q2 "pong")
        (is (= ["pong" "pong"] (take 2 (msg/message-seq q2))))
        (is (not (msg/receive q1 :timeout 10000)))))))

(deftest unschedule
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping")
      (is (msg/receive q :timeout 10000))
      (job/unschedule "a-job")
      (is (not (msg/receive q :timeout 5000))))))

(deftest job-should-have-correct-CL
  (let [q (random-queue)]
      (with-job #(msg/publish q (.toString (.getContextClassLoader (Thread/currentThread))))
        (is (re-find #"ImmutantClassLoader.*deployment\..*\.clj"
                     (msg/receive q :timeout 10000))))))

(deftest job-should-have-the-context-set
  (let [q (random-queue)]
    (with-job #(msg/publish q (instance? org.quartz.JobExecutionContext job/*job-execution-context*))
      (is (msg/receive q :timeout 10000)))))

(deftest it-should-raise-when-spec-is-given-with-at-opts
  (doseq [o [:at :in :every :repeat :until]]
    (is (thrown? IllegalArgumentException (job/schedule "name" #() "spec" o 0)))))

(deftest singleton-false-and-true-should-coexist
  (let [q (random-queue)
        q2 (random-queue)]
    (try
      (job/schedule "singleton-false" #(msg/publish q "hi") "*/1 * * * * ?" :singleton false)
      (job/schedule "singleton-true" #(msg/publish q2 "hi") "*/1 * * * * ?" :singleton true)
      (is (msg/receive q :timeout 10000))
      (is (msg/receive q2 :timeout 10000))
      (finally (job/unschedule "singleton-false")
               (job/unschedule "singleton-true")))))

(deftest reloading-ns-should-not-break-unschedule
  (let [q (random-queue)
        aint (AtomicInteger.)]
    (with-job (fn [] (msg/publish q "ping")
                (.incrementAndGet aint))
      (is (msg/receive q :timeout 10000))
      (require '[immutant.jobs :as job] :reload-all)
      (job/unschedule "a-job")
      (Thread/sleep 5000)
      (let [curval (.get aint)]
        (Thread/sleep 2000)
        (is (= curval (.get aint)))))))
