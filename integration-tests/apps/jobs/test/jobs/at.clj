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
  (let [q (promise)]
    (try
      (job/schedule :job #(deliver q "ping"))
      (is (= "ping" (deref q 1000 :fail)))
      (finally (job/unschedule :job)))))

(deftest an-empty-hash-should-fire-once
  (let [q (atom 0)]
    (with-job #(swap! q inc) []
      (Thread/sleep 200)
      (is (= 1 @q)))))

(deftest in-should-fire-once-in-x-ms
  (let [q (promise)]
    (with-job #(deliver q "ping") [:in 1000]
      (is (nil? (deref q 900 nil)))
      (is (= "ping" (deref q 200 :fail))))))

(deftest at-as-a-date-should-fire-once-then
  (let [q (promise)]
    (with-job  #(deliver q "ping") [:at (Date. (+ 1000 (System/currentTimeMillis)))] 
      (is (nil? (deref q 900 nil)))
      (is (= "ping" (deref q 200 :fail))))))

(deftest at-as-a-long-should-fire-once-then
  (let [q (promise)]
    (with-job  #(deliver q "ping") [:at (+ 1000 (System/currentTimeMillis))] 
      (is (nil? (deref q 900 nil)))
      (is (= "ping" (deref q 200 :fail))))))

(deftest every-should-fire-immediately-and-continuously
  (let [q (atom 0)]
    (with-job #(swap! q inc) [:every 100]
      (dotimes [i 5]
        (Thread/sleep (if (zero? i) 20 100))
        (is (= (inc i) @q))))))

(deftest every-with-repeat-should-fire-immediately-x-times
  (let [q (atom 0)]
    (with-job #(swap! q inc) [:every 100 :repeat 1]
      (Thread/sleep 20)
      (is (= 1 @q))
      (dotimes [_ 2]
        (Thread/sleep 100)
        (is (= 2 @q))))))

(deftest at-with-every-should-fire-immediately-and-continuously-starting-at-at
  (let [q (atom 0)]
    (with-job #(swap! q inc) [:at (+ 1000 (System/currentTimeMillis)) :every 100]
      (Thread/sleep 500)
      (= (zero? @q))
      (dotimes [i 5]
        (Thread/sleep (if (zero? i) 520 100))
        (is (= (inc i) @q))))))

(deftest until-with-every-should-repeat-until-until
  (let [q (atom 0)
        step 222]
    (with-job #(swap! q inc) [:until (+ 1000 (System/currentTimeMillis)) :every step]
      (dotimes [i 5]
        (Thread/sleep (if (zero? i) 20 step))
        (is (= (inc i) @q)))
      (Thread/sleep step)
      (is (= 5 @q)))))

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
