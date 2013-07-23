(ns basic-ring.test.ns-reload
  (:use clojure.test)
  (:refer-clojure :exclude [get])
  (:require [basic-ring.core :as core]
            [immutant.web :as web]
            [immutant.util :as util]
            [clj-http.client :as http]))

(defn get []
  (http/get (str (util/app-uri) "/ns-reload")
            {:throw-exceptions false}))

(deftest reload-of-web-ns-should-not-prevent-stop
  (web/stop "/") ;; get the root handler out of the way
  (web/start "/ns-reload" core/handler)
  (is (= 200 (:status (get))))
  (require '[immutant.web :as web] :reload-all)
  (web/stop "/ns-reload")
  (is (= 404 (:status (get)))))
