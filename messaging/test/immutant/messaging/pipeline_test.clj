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

(ns immutant.messaging.pipeline-test
  (:require [immutant.messaging.pipeline :refer :all]
            [immutant.messaging :as msg]
            [clojure.test :refer :all]
            [immutant.util :as u])
  (:import org.projectodd.wunderboss.WunderBoss
           org.projectodd.wunderboss.messaging.Queue))

(use-fixtures :once
  (fn [f]
    (u/reset-fixture f)
    (reset! @#'immutant.messaging.pipeline/pipelines {})))

(defn random-queue []
  (msg/queue (str (java.util.UUID/randomUUID))))

(defn dollarizer [s]
  (.replace s "S" "$"))

(defn make-sleeper [t]
  (fn [m]
    (Thread/sleep t)
    m))

(defn pipeline-queue-name [pl]
  (let [n (-> pl meta :pipeline .name)]
    (is n)
    n))

(defn chucker [_]
  (throw (Exception. "boom")))

(testing "step"
  (deftest it-should-attach-the-options-as-meta
    (is (= {:concurrency 12 :name :biscuit}
          (meta (step #() :concurrency 12 :name :biscuit)))))

  (deftest it-should-preserve-existing-meta-data
    (is (= (meta (step (with-meta #() {:error-handler :foo})
                   :concurrency 12 :name :biscuit))
          {:concurrency 12 :name :biscuit :error-handler :foo})))

  (deftest it-should-accept-a-map
    (is (= {:concurrency 12 :name :biscuit}
          (meta (step #() {:concurrency 12 :name :biscuit}))))))

(deftest should-call-messaging-stop-with-the-pipeline-from-the-metadata
  (with-redefs [immutant.messaging/stop (fn [q] (is (= :a-queue q)) :called)]
    (is (= :called (stop (with-meta #() {:pipeline :a-queue, :name "pipe"}))))))

(testing "pipeline"
  (deftest should-pass-a-queue-name-based-on-the-given-name
    (let [q-name (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-name (first args)))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline "my-pl")
        (is (re-find #"pipeline-my-pl$" @q-name)))))

  (deftest should-pass-a-queue-name-based-on-the-given-name-even-if-it-is-a-keyword
    (let [q-name (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-name (first args)))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline :pl)
        (is (re-find #"pipeline-pl$" @q-name)))))

  (deftest should-pass-durable-through-to-queue
    (let [q-args (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-args args))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline "my-pl-with-options" :durable :red)
        (is (= [:durable :red] (rest @q-args))))))

  (deftest should-take-a-map-of-args-as-well
    (let [q-args (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-args args))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline "my-pl-with-map-options" {:durable :yellow})
        (is (= [:durable :yellow] (rest @q-args)))))))

(testing "the returned value"

  (deftest should-have-the-pipeline-queue-as-metadata
    (let [q (-> "name" pipeline meta :pipeline)]
      (is (instance? Queue q))
      (is (re-find #"pipeline-name$" (.name q)))))

  (deftest should-have-the-listeners-as-metadata
    (with-redefs [immutant.messaging.pipeline/pipeline-listen (constantly :l)]
      (is (= [:l :l :l] (-> "ham" (pipeline #() #()) meta :listeners)))))

  (deftest should-be-a-fn
    (is (fn? (pipeline :foo))))

  (deftest should-raise-if-the-named-pl-already-exists
    (pipeline "shamwow")
    (is (thrown? IllegalArgumentException
          (pipeline "shamwow"))))

  (deftest should-raise-if-given-a-non-existent-step
    (is (thrown? IllegalArgumentException
          ((pipeline "slapchop" (step #() :name :biscuit))
           nil :step :ham))))

  (deftest should-raise-if-a-disabled-result-pipeline-is-derefed
    (is (thrown? IllegalStateException
          @((pipeline "boom" identity :result-ttl -1) :foo)))))

(deftest invalid-opts
  (is (thrown-with-msg? IllegalArgumentException
        #"is not a valid option" (pipeline "name" :ham :biscuit)))
  (is (thrown-with-msg? IllegalArgumentException
        #"is not a valid option" (step println :ham :biscuit))))

(deftest pipelines-should-be-useable-inside-pipelines
  (let [p1 (pipeline
             "inner"
             (memfn toUpperCase))
        p2 (pipeline
             "outer"
             p1
             dollarizer)]
    (is (= "FANTA$TIC" (deref (p2 "fantastic")
                         1000 :failure)))))

(deftest it-should-work
  (let [pl (pipeline
             "basic"
             #(.replace % "m" "x")
             (memfn toUpperCase)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAXBIKe$haCUIT" (deref (pl "hambiscuit")
                              1000 :failure)))))

(deftest halt-should-work
  (let [result-queue (random-queue)
        pl (pipeline
             "halt"
             (fn [m]
               (msg/publish result-queue (dollarizer m))
               halt))
        result (pl "hambiScuit")]
    (is (= "hambi$cuit" (msg/receive result-queue)))
    (is (nil? (deref result 1000 nil)))))

(deftest it-should-work-with-a-step-name-on-publish
  (let [pl (pipeline
             "basic-step"
             #(.replace % "m" "x")
             (step (memfn toUpperCase) :name :uc)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAMBIKe$haCUIT" (deref (pl "hambiscuit" :step :uc)
                              1000 :failure)))))

(deftest it-should-work-with-a-numeric-name-on-publish
  (let [pl (pipeline
             "numeric-step"
             #(.replace % "m" "x")
             (memfn toUpperCase)
             dollarizer
             #(.replace % "$" "Ke$ha"))]
    (is (= "HAMBIKe$haCUIT" (deref (pl "hambiscuit" :step 1)
                              1000 :failure)))))

(deftest it-should-work-with-concurrency
  (let [result-queue (random-queue)
        pl (pipeline
             "concurrency"
             (step (fn [_]
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
        pl (pipeline
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
        pl (pipeline
             "concurrency-global-override"
             (step  (fn [_]
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
        pl1 (pipeline
              :global-timeout-sleepy
              (make-sleeper 500))
        pl2 (pipeline
              :global-timeout
              pl1
              identity
              :step-deref-timeout 1
              :error-handler (fn [e _] (msg/publish err-queue (str e))))]
    (pl2 :ham)
    (is (re-find #"Timed out after 1" (msg/receive err-queue)))))

(deftest step-step-deref-timeout-should-override-global
  (let [err-queue (random-queue)
        pl1 (pipeline
              :step-timeout-sleepy
              (make-sleeper 500))
        pl2 (pipeline
              :step-timeout
              (step pl1 :step-deref-timeout 1)
              identity
              :step-deref-timeout 1000
              :error-handler (fn [e _] (msg/publish err-queue (str e))))]
    (pl2 :ham)
    (is (re-find #"Timed out after 1" (msg/receive err-queue)))))

(deftest *pipeline*-should-be-bound
  (let [p (promise)
        pl (pipeline
             "pipeline var"
             (fn [_] (deliver p (pipeline-queue-name *pipeline*))))]
    (pl "hi")
    (is (= (pipeline-queue-name pl)
          (deref p 1000 :failure)))))

(deftest *current-step*-and-*next-step*-should-be-bound
  (let [p (promise)
        pl (pipeline
             "step vars"
             (fn [_] (deliver p [*current-step* *next-step*]))
             identity)]
    (pl "hi")
    (is (= ["0" "1"] (deref p
                       1000 :failure)))))

(deftest *current-step*-and-*next-step*-should-be-bound-when-steps-are-named
  (let [p (promise)
        pl (pipeline
             "step vars redux"
             (step
               (fn [_]
                 (deliver p [*current-step* *next-step*]))
               :name "one")
             (step identity :name "two"))]
    (pl "hi")
    (is (= ["one" "two"] (deref p
                           1000 :failure)))))

(deftest result-ttl-should-be-honored
  (let [pl (pipeline
             :result-ttl
             identity
             :result-ttl 1)
        result (pl :foo)]
    (Thread/sleep 100)
    (is (nil? (deref result 1000 nil)))))

(testing "error handling"
  (deftest global-error-handling-should-work
    (let [p (promise)
          pl (pipeline
               "global-eh"
               chucker
               :error-handler (fn [e m]
                                (deliver p :you-are-out)))]
      (pl "hi")
      (is (= :you-are-out (deref p 1000 :failure)))))

  (deftest step-error-handling-should-work
    (let [p (promise)
          pl (pipeline
               "step-eh"
               (step chucker
                 :error-handler (fn [e m]
                                  (deliver p "from step"))))]
      (pl "hi")
      (is (= "from step" (deref p 1000 :failure)))))

  (deftest step-error-handling-should-override-global
    (let [p (promise)
          pl (pipeline
               "step-eh-override"
               (step chucker
                 :error-handler (fn [e m]
                                  (deliver p "from step")))
               :error-handler (fn [e m]
                                (deliver p "from global")))]
      (pl "hi")
      (is (= "from step" (deref p 1000 :failure)))))

  (deftest *pipeline*-should-be-bound-in-an-error-handler
    (let [p (promise)
          pl (pipeline
               "pipeline var eh"
               chucker
               :error-handler (fn [_ _] (deliver p (pipeline-queue-name *pipeline*))))]
      (pl "hi")
      (is (= (pipeline-queue-name pl)
            (deref p 1000 :failure)))))

  (deftest *current-step*-and-*next-step*-should-be-bound-in-an-error-handler
    (let [p (promise)
          pl (pipeline
               "step vars eh"
               chucker
               (fn [_])
               :error-handler (fn [_ _] (deliver p [*current-step* *next-step*])))]
      (pl "hi")
      (is (= ["0" "1"] (deref p 1000 :failure))))))

(defrecord TestRecord [a])

(deftest with-a-record
  (if (u/in-container?)
    (let [pl (pipeline :with-a-record #(update-in % [:a] inc))]
      (is (= (->TestRecord 2) (deref (pl (->TestRecord 1)) 10000 :timeout))))
    (println "NOTE: pipeline record test disabled out-of-container, as it will fail. TODO: fix it")))

(deftest pipeline-returning-nil-should-return-nil-instead-of-timeout-val
  (let [pl (pipeline :nil-pl (constantly nil))]
    (is (nil? (deref (pl :foo) 10000 :timeout)))))

(deftest pipeline-timing-out-should-return-timeout-val
  (let [pl (pipeline :timeout-val-pl #(Thread/sleep %))]
    (is (= :ham (deref (pl 1000) 1 :ham)))))

(deftest reloading-pipeline-ns-should-not-reset-internal-state
  (let [name :reloading-ns-pl]
    (pipeline name)
    (require '[immutant.messaging.pipeline :as pl] :reload-all)
    (is (thrown? IllegalArgumentException
          (pipeline name)))))

(deftest fanout-test
  (let [result-queue (random-queue)
        pl (pipeline
             :fanout
             fanout
             (partial msg/publish result-queue))]
    (pl (repeat 5 :a))
    (dotimes [_ 5]
      (is (= :a (msg/receive result-queue :timeout 10000))))))

(deftest fanout-step-test
  (let [result-queue (random-queue)
        pl (pipeline
             :fanout-step
             (step identity :fanout? true)
             #(msg/publish result-queue %))]
    (pl (repeat 5 :a))
    (dotimes [_ 5]
      (is (= :a (msg/receive result-queue :timeout 10000))))))
