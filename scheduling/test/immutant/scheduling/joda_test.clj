;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(ns immutant.scheduling.joda-test
  (:require [clojure.test :refer :all]
            [immutant.scheduling.joda :refer :all]
            [immutant.scheduling :as s]
            [immutant.scheduling.options :refer [resolve-options]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]))

(deftest schedule-period-sequence
  (let [result (atom [])
        now (t/now)]
    (with-redefs [s/schedule (fn [f {t :at}] (swap! result conj t) (f))]
      (schedule-seq #() (take 10 (periodic-seq now (t/minutes 42)))))
    (is (= (repeat 9 42) (map (comp t/in-minutes (partial apply t/interval)) (partition 2 1 @result))))))

(deftest option-resolution
  (let [now (t/now)]
    (is (=  {:at (.toDate now)} (resolve-options {:at now})))))
