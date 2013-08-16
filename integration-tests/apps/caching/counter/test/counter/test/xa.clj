(ns counter.test.xa
  (:use clojure.test)
  (:require [immutant.xa :as xa]
            [immutant.cache :as cache]
            [immutant.messaging :as msg]
            [immutant.util :as util]
            [clojure.tools.logging :as log]))

(def q "/queue/counter.test.xa")
(msg/start q :durable false)

(deftest listener-get-should-match-transactional-put
  (let [cache (cache/create "counter.test.xa")
        result (atom [])
        listener (msg/listen q #(swap! result conj (get cache %)))]
    (dotimes [x 500]
      (xa/transaction
       (cache/put cache x x)
       (msg/publish q x)))
    (util/wait-for #(= 500 (count @result)))
    (is (not-any? nil? @result))))

