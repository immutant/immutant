(ns basic-ring.test.reload
  (:use clojure.test
        basic-ring.reload)
  (:require [immutant.web :as web]
            [immutant.util :as util]
            [clj-http.client :as http]))

(defn drool [v]
  (let [file (util/app-relative "src/basic_ring/spittle.clj")]
    (spit file (str "(ns basic-ring.spittle) (defn content [] \"" v "\")"))
    (Thread/sleep 1000)))

(deftest test-reload-changes
  (let [path "/test-reload"]
    (web/start "/test-reload" reload-test-handler)
    (is (= "foo" (:body (http/get (str (util/app-uri) path)))))
    (drool "bar")
    (is (= "bar" (:body (http/get (str (util/app-uri) path)))))
    (web/start "/test-reload" #'reload-test-handler)
    (is (= "bar" (:body (http/get (str (util/app-uri) path)))))
    (drool "foo")
    (is (= "foo" (:body (http/get (str (util/app-uri) path)))))))

