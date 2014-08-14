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

(ns messaging.pipeline-test
  (:require [clojure.test :refer :all]
            [immutant.messaging.pipeline :as pl]
            [immutant.messaging :as msg]
            [immutant.util :refer [in-container?]])
  (:import org.projectodd.wunderboss.WunderBoss))

(use-fixtures :once
  (fn [f]
    (f)
    (when-not (in-container?)
      (WunderBoss/shutdownAndReset)
      (reset! @#'pl/pipelines {}))))

(defn random-queue []
  (msg/queue (str (java.util.UUID/randomUUID))))

(defn dollarizer [s]
  (.replace s "S" "$"))

(defn make-sleeper [t]
  (fn [m]
    (Thread/sleep t)
    m))

(defn p [v]
  (println v)
  v)

(defn pipeline-queue-name [pl]
  (let [n (-> pl meta :pipeline :destination .name)]
    (is n)
    n))

(defn chucker [_]
  (throw (Exception. "boom")))

(deftest it-should-work
  (let [pl (pl/pipeline
             "basic"
             #(.replace % "m" "x")
             (memfn toUpperCase)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAXBIKe$haCUIT" (deref (pl "hambiscuit")
                              1000 :failure)))))

(deftest halt-should-work
  (let [result-queue (random-queue)
        pl (pl/pipeline
             "halt"
             (fn [m]
               (msg/publish result-queue (dollarizer m))
               pl/halt))
        result (pl "hambiScuit")]
    (is (= "hambi$cuit" (msg/receive result-queue)))
    (is (nil? (deref result 1000 nil)))))

(deftest it-should-work-with-a-step-name-on-publish
  (let [pl (pl/pipeline
             "basic-step"
             #(.replace % "m" "x")
             (pl/step (memfn toUpperCase) :name :uc)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAMBIKe$haCUIT" (deref (pl "hambiscuit" :step :uc)
                              1000 :failure)))))

(deftest it-should-work-with-a-numeric-name-on-publish
  (let [pl (pl/pipeline
             "numeric-step"
             #(.replace % "m" "x")
             (memfn toUpperCase)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAMBIKe$haCUIT" (deref (pl "hambiscuit" :step 1)
                              1000 :failure)))))

(deftest it-should-work-with-concurrency
  (let [result-queue (random-queue)
        pl (pl/pipeline
             "concurrency"
             (pl/step (fn [_]
                        (Thread/sleep 500)
                        (.getName (Thread/currentThread))) :concurrency 5)
             (partial msg/publish result-queue)
             :result-ttl -1)]
    (dotimes [n 10]
      (pl "yo"))
    (let [results (->> (range 10)
                    (map (fn [_] (msg/receive result-queue :timeout 400)))
                    set)]
      (is (<= 2 (count results))))))

(deftest it-should-work-with-global-concurrency
  (let [result-queue (random-queue)
        pl (pl/pipeline
             "concurrency-global"
             (fn [_]
               (Thread/sleep 500)
               (.getName (Thread/currentThread)))
             (partial msg/publish result-queue)
             :concurrency 5
             :result-ttl -1)]
    (dotimes [n 10]
      (pl "yo"))
    (let [results (->> (range 10)
                    (map (fn [_] (msg/receive result-queue :timeout 400)))
                    set)]
      (is (<= 2 (count results))))))

(deftest step-concurrency-should-override-global
  (let [result-queue (random-queue)
        pl (pl/pipeline
             "concurrency-global-override"
             (pl/step  (fn [_]
                         (Thread/sleep 500)
                         (.getName (Thread/currentThread))) :concurrency 5)
             (partial msg/publish result-queue)
             :concurrency 1
             :result-ttl -1)]
    (dotimes [n 10]
      (pl "yo"))
    (let [results (->> (range 10)
                    (map (fn [_] (msg/receive result-queue :timeout 400)))
                    (remove nil?)
                    set)]
      (is (<= 2 (count results))))))

(deftest it-should-work-with-global-step-deref-timeout
  (let [err-queue (random-queue)
        pl1 (pl/pipeline
              :global-timeout-sleepy
              (make-sleeper 500))
        pl2 (pl/pipeline
              :global-timeout
              pl1
              identity
              :step-deref-timeout 1
              :error-handler (fn [e _] (msg/publish err-queue (str e))))]
    (pl2 :ham)
    (is (re-find #"Timed out after 1" (msg/receive err-queue)))))

(deftest step-step-deref-timeout-should-override-global
  (let [err-queue (random-queue)
        pl1 (pl/pipeline
              :step-timeout-sleepy
              (make-sleeper 500))
        pl2 (pl/pipeline
              :step-timeout
              (pl/step pl1 :step-deref-timeout 1)
              identity
              :step-deref-timeout 1000
              :error-handler (fn [e _] (msg/publish err-queue (str e))))]
    (pl2 :ham)
    (is (re-find #"Timed out after 1" (msg/receive err-queue)))))

(deftest *pipeline*-should-be-bound
  (let [p (promise)
        pl (pl/pipeline
             "pipeline var"
             (fn [_] (deliver p (pipeline-queue-name pl/*pipeline*))))]
    (pl "hi")
    (is (= (pipeline-queue-name pl)
          (deref p 1000 :failure)))))

(deftest *current-step*-and-*next-step*-should-be-bound
  (let [p (promise)
        pl (pl/pipeline
             "step vars"
             (fn [_] (deliver p [pl/*current-step* pl/*next-step*]))
             identity)]
    (pl "hi")
    (is (= ["0" "1"] (deref p
                       1000 :failure)))))

(deftest *current-step*-and-*next-step*-should-be-bound-when-steps-are-named
  (let [p (promise)
        pl (pl/pipeline
             "step vars redux"
             (pl/step
               (fn [_]
                 (deliver p [pl/*current-step* pl/*next-step*]))
               :name "one")
             (pl/step identity :name "two"))]
    (pl "hi")
    (is (= ["one" "two"] (deref p
                           1000 :failure)))))

(deftest result-ttl-should-be-honored
  (let [pl (pl/pipeline
             :result-ttl
             identity
             :result-ttl 1)
        result (pl :foo)]
    (Thread/sleep 100)
    (is (nil? (deref result 1000 nil)))))

(testing "error handling"
  (deftest global-error-handling-should-work
    (let [p (promise)
          pl (pl/pipeline
               "global-eh"
               chucker
               :error-handler (fn [e m]
                                (deliver p :you-are-out)))]
      (pl "hi")
      (is (= :you-are-out (deref p 1000 :failure)))))

  (deftest step-error-handling-should-work
    (let [p (promise)
          pl (pl/pipeline
               "step-eh"
               (pl/step chucker
                 :error-handler (fn [e m]
                                  (deliver p "from step"))))]
      (pl "hi")
      (is (= "from step" (deref p 1000 :failure)))))

  (deftest step-error-handling-should-override-global
    (let [p (promise)
          pl (pl/pipeline
               "step-eh-override"
               (pl/step chucker
                 :error-handler (fn [e m]
                                  (deliver p "from step")))
               :error-handler (fn [e m]
                                (deliver p "from global")))]
      (pl "hi")
      (is (= "from step" (deref p 1000 :failure)))))

  (deftest *pipeline*-should-be-bound-in-an-error-handler
    (let [p (promise)
          pl (pl/pipeline
               "pipeline var eh"
               chucker
               :error-handler (fn [_ _] (deliver p (pipeline-queue-name pl/*pipeline*))))]
      (pl "hi")
      (is (= (pipeline-queue-name pl)
            (deref p 1000 :failure)))))

  (deftest *current-step*-and-*next-step*-should-be-bound-in-an-error-handler
    (let [p (promise)
          pl (pl/pipeline
               "step vars eh"
               chucker
               (fn [_])
               :error-handler (fn [_ _] (deliver p [pl/*current-step* pl/*next-step*])))]
      (pl "hi")
      (is (= ["0" "1"] (deref p 1000 :failure))))))

(defrecord TestRecord [a])

(deftest with-a-record
  (if (in-container?)
    (let [pl (pl/pipeline :with-a-record #(update-in % [:a] inc))]
      (is (= (->TestRecord 2) (deref (pl (->TestRecord 1)) 10000 :timeout))))
    (println "NOTE: pipeline record test disabled out-of-container, as it will fail. TODO: fix it")))

(deftest pipeline-returning-nil-should-return-nil-instead-of-timeout-val
  (let [pl (pl/pipeline :nil-pl (constantly nil))]
    (is (nil? (deref (pl :foo) 10000 :timeout)))))

(deftest pipeline-timing-out-should-return-timeout-val
  (let [pl (pl/pipeline :timeout-val-pl #(Thread/sleep %))]
    (is (= :ham (deref (pl 1000) 1 :ham)))))

(deftest reloading-pipeline-ns-should-not-reset-internal-state
  (let [name :reloading-ns-pl]
    (pl/pipeline name)
    (require '[immutant.messaging.pipeline :as pl] :reload-all)
    (is (thrown? IllegalArgumentException
          (pl/pipeline name)))))

(deftest fanout
  (let [result-queue (random-queue)
        pl (pl/pipeline
             :fanout
             pl/fanout
             (partial msg/publish result-queue))]
    (pl (repeat 5 :a))
    (dotimes [_ 5]
      (is (= :a (msg/receive result-queue :timeout 10000))))))

(deftest fanout-step
  (let [result-queue (random-queue)
        pl (pl/pipeline
             :fanout-step
             (pl/step identity :fanout? true)
             #(msg/publish result-queue %))]
    (pl (repeat 5 :a))
    (dotimes [_ 5]
      (is (= :a (msg/receive result-queue :timeout 10000))))))
