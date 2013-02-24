(ns basic-ring.test.core
  (:use basic-ring.core
        clojure.test)
  (:require [immutant.web :as web]))

(deftest init-and-destroy-fns
  (let [result (atom [])
        init (fn [] (swap! result conj :init-success))
        destroy (fn [] (swap! result conj :destroy-success))]
    (web/start "/init-and-destroy" handler :init init, :destroy destroy)
    (is (= @result [:init-success]))
    (web/stop "/init-and-destroy")
    (is (= @result [:init-success :destroy-success]))))
