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
            [clojure.test :refer :all]
            [immutant.util :as u])
  (:import org.projectodd.wunderboss.WunderBoss
           org.projectodd.wunderboss.messaging.Queue))

(use-fixtures :once
  (fn [f]
    (u/reset-fixture f)
    (reset! @#'immutant.messaging.pipeline/pipelines {})))

(defn dollarizer [s]
  (.replace s "S" "$"))

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
        (is (= ".pipeline-my-pl" @q-name)))))

  (deftest should-pass-a-queue-name-based-on-the-given-name-even-if-it-is-a-keyword
    (let [q-name (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-name (first args)))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline :pl)
        (is (= ".pipeline-pl" @q-name)))))

  (deftest should-pass-durable-through-to-queue
    (let [q-args (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-args args))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline "my-pl-with-options" :durable :red)
        (is (= [".pipeline-my-pl-with-options" :durable :red] @q-args)))))

  (deftest should-take-a-map-of-args-as-well
    (let [q-args (atom nil)]
      (with-redefs [immutant.messaging/queue (fn [& args] (reset! q-args args))
                    immutant.messaging.pipeline/pipeline-listen (constantly nil)]
        (pipeline "my-pl-with-map-options" {:durable :yellow})
        (is (= [".pipeline-my-pl-with-map-options" :durable :yellow] @q-args))))))

(testing "the returned value"

  (deftest should-have-the-pipeline-queue-as-metadata
    (let [q (-> "name" pipeline meta :pipeline :destination)]
      (is (instance? Queue q))
      (is (= ".pipeline-name" (.name q)))))

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
