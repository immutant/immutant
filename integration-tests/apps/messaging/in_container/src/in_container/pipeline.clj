(ns in-container.pipeline
  (:use clojure.test)
  (:require [immutant.pipeline  :as pl]
            [immutant.messaging :as msg]))

(defn random-queue []
  (let [q (msg/as-queue (str (java.util.UUID/randomUUID)))]
    (msg/start q)
    q))

(defn dollarizer [s]
  (.replace s "S" "$"))

(deftest it-should-work
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "basic"
            #(.replace % "m" "x")
            (memfn toUpperCase)
            dollarizer
            #(.replace % "$" "Ke$ha")
            (partial msg/publish result-queue))]
    (pl "hambiscuit")
    (is (= "HAXBIKe$haCUIT" (msg/receive result-queue)))))

(deftest halt-should-work
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "halt"
            (fn [m]
              (msg/publish result-queue (dollarizer m))
              pl/halt)
            (partial msg/publish result-queue))]
    (pl "hambiScuit")
    (is (= "hambi$cuit" (msg/receive result-queue)))
    (is (nil? (msg/receive result-queue :timeout 2000)))))

(deftest it-should-work-with-a-step-name-on-publish
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "basic-step"
            #(.replace % "m" "x")
            (pl/step (memfn toUpperCase) :name :uc)
            dollarizer
            #(.replace % "$" "Ke$ha")
            (partial msg/publish result-queue))]
    (pl "hambiscuit" :step :uc)
    (is (= "HAMBIKe$haCUIT" (msg/receive result-queue)))))

(deftest it-should-work-with-a-numeric-name-on-publish
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "numeric-step"
            #(.replace % "m" "x")
            (memfn toUpperCase)
            dollarizer
            #(.replace % "$" "Ke$ha")
            (partial msg/publish result-queue))]
    (pl "hambiscuit" :step 1)
    (is (= "HAMBIKe$haCUIT" (msg/receive result-queue)))))

(deftest it-should-work-with-concurrency
  (let [result-queue (random-queue)
        pl (pl/pipeline
             "concurrency"
             (pl/step (fn [_]
                        (Thread/sleep 500)
                        (.getName (Thread/currentThread))) :concurrency 5)
             (partial msg/publish result-queue))]
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
             :concurrency 5)]
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
             :concurrency 1)]
    (dotimes [n 10]
      (pl "yo"))
    (let [results (->> (range 10)
                       (map (fn [_] (msg/receive result-queue :timeout 400)))
                       (remove nil?)
                       set)]
      (is (<= 2 (count results))))))

(deftest *pipeline*-should-be-bound
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "pipeline var"
              (fn [_] (msg/publish result-queue (meta pl/*pipeline*))))]
      (pl "hi")
      (is (= (-> pl meta :pipeline) (:pipeline (msg/receive result-queue))))))

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
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step vars"
              (fn [_] (msg/publish result-queue [pl/*current-step* pl/*next-step*]))
              (fn [_]))]
      (pl "hi")
      (is (= ["0" "1"] (msg/receive result-queue)))))

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
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step vars redux"
              (pl/step
               (fn [_]
                 (msg/publish result-queue [pl/*current-step* pl/*next-step*]))
               :name "one")
              (pl/step
               (fn [_])
               :name "two"))]
      (pl "hi")
      (is (= ["one" "two"] (msg/receive result-queue)))))

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



