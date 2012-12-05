(ns in-container.pipeline
  (:use clojure.test)
  (:require [immutant.messaging.pipeline :as pl]
            [immutant.messaging          :as msg]))

(defn pio [f]
  (fn [i]
    (println "INPUT:" i)
    (let [o (f i)]
      (println "OUTPUT:" o)
      o)))

(defn random-queue []
  (msg/as-queue (str (java.util.UUID/randomUUID))))

(defn dollarizer [s]
  (.replace s "S" "$"))

(defn sleeper [x]
  (Thread/sleep 500)
  x)

(deftest it-should-work
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "basic"
            #(.replace % "m" "x")
            (memfn toUpperCase)
            dollarizer
            #(.replace % "$" "Ke$ha")
            #(msg/publish result-queue %))]
    (msg/start result-queue)
    (msg/publish pl "hambiscuit")
    (is (= "HAXBIKe$haCUIT" (msg/receive result-queue)))))

(deftest it-should-work-with-a-result-queue
  (let [result-queue "queue.pl.result-opt"
        pl (pl/pipeline "result-queue"
                        #(.replace % "y" "x")
                        (memfn toUpperCase)
                        dollarizer
                        #(.replace % "$" "NipseyHu$$le")
                        :result-destination result-queue)]
    (msg/publish pl "gravybiscuit")
    (is (= "GRAVXBINipseyHu$$leCUIT" (msg/receive result-queue)))))

(deftest it-should-work-with-concurrency
  (let [result-queue (random-queue)
        pl (pl/pipeline
            "concurrency"
            dollarizer
            (pl/step sleeper :concurrency 10)
            :result-destination result-queue)]
    (dotimes [n 10]
      (msg/publish pl "hamboneS"))
    (let [results
          (keep identity
                (map (fn [_] (msg/receive result-queue :timeout 510))
                     (range 10)))]
      (is (= 10 (count results)))
      (is (= (take 10 (repeat "hambone$")) results)))))

(deftest *pipeline*-should-be-bound
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "pipeline var"
              (fn [_] (msg/publish result-queue (str pl/*pipeline*))))]
      (msg/start result-queue)
      (msg/publish pl "hi")
      (is (= (str pl) (msg/receive result-queue)))))

(defn chucker [_]
  (throw (Exception. "boom")))

(testing "error handling"
  (deftest global-error-handling-should-work
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "global-eh"
              chucker
              :error-handler (fn [e m]
                               (msg/publish result-queue "caught it!")))]
      (msg/start result-queue)
      (msg/publish pl "hi")
      (is (= "caught it!" (msg/receive result-queue)))))

  (deftest step-error-handling-should-work
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "step-eh"
              (pl/step chucker
                       :error-handler (fn [e m]
                                        (msg/publish result-queue "from step"))))]
      (msg/start result-queue)
      (msg/publish pl "hi")
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
      (msg/start result-queue)
      (msg/publish pl "hi")
      (is (= "from step" (msg/receive result-queue)))))

  (deftest *pipeline*-should-be-bound-in-an-error-handler
    (let [result-queue (random-queue)
          pl (pl/pipeline
              "pipeline var in eh"
              chucker
              :error-handler (fn [e m]
                               (msg/publish result-queue (str pl/*pipeline*))))]
      (msg/start result-queue)
      (msg/publish pl "hi")
      (is (= (str pl) (msg/receive result-queue))))))



