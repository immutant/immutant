(ns in-container.test.pipeline
  (:use clojure.test)
  (:require [immutant.pipeline  :as pl]
            [immutant.messaging :as msg]))

(defn random-queue []
  (let [q (msg/as-queue (str (java.util.UUID/randomUUID)))]
    (msg/start q)
    q))

(defn dollarizer [s]
  (.replace s "S" "$"))

(defn make-sleeper [t]
  (fn [m]
    (Thread/sleep t)
    m))

(deftest it-should-work
  (let [pl (pl/pipeline
            "basic"
            #(.replace % "m" "x")
            (memfn toUpperCase)
            dollarizer
            #(.replace % "$" "Ke$ha"))]
    (is (= "HAXBIKe$haCUIT" @(pl "hambiscuit")))))

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
        (is (= "HAMBIKe$haCUIT" @(pl "hambiscuit" :step :uc)))))

(deftest it-should-work-with-a-numeric-name-on-publish
  (let [pl (pl/pipeline
            "numeric-step"
            #(.replace % "m" "x")
            (memfn toUpperCase)
            dollarizer
            #(.replace % "$" "Ke$ha"))]
        (is (= "HAMBIKe$haCUIT" @(pl "hambiscuit" :step 1)))))

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
    (let [pl (pl/pipeline
              "pipeline var"
              (fn [_] (meta pl/*pipeline*)))]
      (is (= (-> pl meta :pipeline) (:pipeline @(pl "hi"))))))

(defn chucker [_]
  (throw (Exception. "boom")))

(deftest *pipeline*-should-be-bound-in-an-error-handler
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "pipeline var eh"
              chucker
              :error-handler (fn [_ _] (msg/publish result-queue (meta pl/*pipeline*))))]
      (pl "hi")
      (is (= (-> pl meta :pipeline) (:pipeline (msg/receive result-queue))))))

(deftest *current-step*-and-*next-step*-should-be-bound
    (let [pl (pl/pipeline
              "step vars"
              (fn [_] [pl/*current-step* pl/*next-step*])
              identity)]
      (is (= ["0" "1"] @(pl "hi")))))

(deftest *current-step*-and-*next-step*-should-be-bound-in-an-error-handler
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step vars eh"
              chucker
              (fn [_])
              :error-handler (fn [_ _] (msg/publish result-queue [pl/*current-step* pl/*next-step*])))]
      (pl "hi")
      (is (= ["0" "1"] (msg/receive result-queue)))))

(deftest *current-step*-and-*next-step*-should-be-bound-when-steps-are-named
    (let [pl (pl/pipeline
              "step vars redux"
              (pl/step
               (fn [_]
                 [pl/*current-step* pl/*next-step*])
               :name "one")
              (pl/step identity :name "two"))]
      (is (= ["one" "two"] @(pl "hi")))))

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
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "global-eh"
              chucker
              :error-handler (fn [e m]
                               (msg/publish result-queue "caught it!")))]
      (pl "hi")
      (is (= "caught it!" (msg/receive result-queue)))))

  (deftest step-error-handling-should-work
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step-eh"
              (pl/step chucker
                       :error-handler (fn [e m]
                                        (msg/publish result-queue "from step"))))]
      (pl "hi")
      (is (= "from step" (msg/receive result-queue)))))

  (deftest step-error-handling-should-override-global
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step-eh-override"
              (pl/step chucker
                       :error-handler (fn [e m]
                                        (msg/publish result-queue "from step")))
              :error-handler (fn [e m]
                               (msg/publish result-queue "from global")))]
      (pl "hi")
      (is (= "from step" (msg/receive result-queue)))))

  (deftest *pipeline*-should-be-bound-in-an-error-handler
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "pipeline var in eh"
              chucker
              :error-handler (fn [e m]
                               (msg/publish result-queue (meta pl/*pipeline*))))]
      (pl "hi")
      (is (= (-> pl meta :pipeline) (:pipeline (msg/receive result-queue)))))))

(deftest pipelines-should-be-useable-inside-pipelines
  (let [p1 (pl/pipeline
            "inner"
            (memfn toUpperCase))
        p2 (pl/pipeline
            "outer"
            p1
            dollarizer)]
    (is (= "FANTA$TIC" @(p2 "fantastic")))))
