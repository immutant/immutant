(ns jobs.at
  (:use clojure.test
        jobs.helper)
  (:require [immutant.jobs :as job]
            [immutant.messaging :as msg])
  (:import java.util.Date))

(defmacro with-job [action spec & body]
  `(try
     (job/schedule "a-job" ~action ~@spec)
     ~@body
     (finally (job/unschedule "a-job"))))

(deftest should-accept-a-keyword-name
  (let [q (random-queue)]
    (try
      (job/schedule :job #(msg/publish q "ping"))
      (is (= "ping" (msg/receive q :timeout 10000)))
      (finally (job/unschedule :job)))))

(deftest an-empty-hash-should-fire-once
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") []
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (not (msg/receive q :timeout 5000))))))

(deftest in-should-fire-once-in-x-ms
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") [:in 5000] 
      (is (not (msg/receive q :timeout 4000)))
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (not (msg/receive q :timeout 5000))))))

(deftest at-as-a-date-should-fire-once-then
  (let [q (random-queue)]
    (with-job  #(msg/publish q "ping") [:at (Date. (+ 5000 (System/currentTimeMillis)))] 
      (is (not (msg/receive q :timeout 4000)))
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (not (msg/receive q :timeout 5000))))))

(deftest at-as-a-long-should-fire-once-then
  (let [q (random-queue)]
    (with-job  #(msg/publish q "ping") [:at (+ 5000 (System/currentTimeMillis))] 
      (is (not (msg/receive q :timeout 4000)))
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (not (msg/receive q :timeout 5000))))))

(deftest every-should-fire-immediately-and-continuously
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") [:every 500] 
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (= ["ping" "ping" "ping"] (take 3 (msg/message-seq q :timeout 550)))))))

(deftest every-with-repeat-should-fire-immediately-x-times
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") [:every 500 :repeat 1] 
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (= ["ping" nil] (take 2 (msg/message-seq q :timeout 550)))))))

(deftest at-with-every-should-fire-immediately-and-continuously-starting-at-at
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") [:at (+ 5000 (System/currentTimeMillis)) :every 500] 
      (is (not (msg/receive q :timeout 4000)))
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (= ["ping" "ping" "ping"] (take 3 (msg/message-seq q :timeout 550)))))))

(deftest until-with-every-should-repeat-until-until
  (let [q (random-queue)]
    (with-job #(msg/publish q "ping") [:until (+ 2000 (System/currentTimeMillis)) :every 500] 
      (is (= "ping" (msg/receive q :timeout 10000)))
      (is (= ["ping" "ping" "ping" nil] (take 4 (msg/message-seq q :timeout 550)))))))

(deftest at-with-in-should-throw
  (is (thrown?
       IllegalArgumentException
       (job/schedule "boom" (fn []) :at 5 :in 5))))  

(deftest repeat-without-every-should-throw
  (is (thrown?
       IllegalArgumentException
       (job/schedule "boom" (fn []) :repeat 5))))

(deftest until-without-every-should-throw
  (is (thrown?
       IllegalArgumentException
       (job/schedule "boom" (fn []) :until 5)))) 
