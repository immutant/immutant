(ns counter.init
  (:use [ring.util.response])
  (:require [immutant.cache :as cache]
            [immutant.web :as web]))

(def data (cache/cache "counts" :replicated))

(defn handler [{path-info :path-info}]
  (let [val (inc (get data path-info -1))]
    (println "value=" val)
    (assoc data path-info val)
    (response (str "value=" val "\n"))))

(web/start "/" handler)
