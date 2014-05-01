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

(ns immutant.web-test
  (:require [clojure.test :refer :all]
            [immutant.web :refer :all]
            [testing.web  :refer [get-body hello handler]]
            [testing.hello.service :as pedestal])
  (:import org.projectodd.wunderboss.WunderBoss
           clojure.lang.ExceptionInfo
           java.net.ConnectException))

(use-fixtures :each
  (fn [f]
    (try
      (f)
      (finally
        (WunderBoss/shutdownAndReset)))))

(def url "http://localhost:8080/")
(def url2 "http://localhost:8081/")

(deftest mount-pedestal-service
  (start pedestal/servlet)
  (is (= "Hello World!" (get-body url))))

(deftest start-returns-opts-with-server-included
  (is (= [:server] (keys (start hello))))
  (let [opts (start {:context-path "/abc"} hello)]
    (is (= #{:server :context-path} (set (keys opts))))
    (is (= "/abc" (:context-path opts)))))

(deftest start-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (start {:invalid true} hello))))

(deftest stop-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (stop {:invalid true}))))

(deftest stop-without-args-stops-default-context
  (start hello)
  (is (= "hello" (get-body url)))
  (start {:context-path "/howdy"} (handler "howdy"))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop)
  (is (= "howdy" (get-body (str url "howdy"))))
  (let [result (is (thrown? ExceptionInfo (get-body url)))]
    (is (= 404 (-> result ex-data :object :status)))))

(deftest stop-with-context-stops-that-context
  (start hello)
  (is (= "hello" (get-body url)))
  (start {:context-path "/howdy"} (handler "howdy"))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop {:context-path "/howdy"})
  (is (= "hello" (get-body url)))
  (is (= "hello" (get-body (str url "howdy")))))

(deftest stopping-last-handler-stops-the-server
  (let [root-opts (start hello)]
    (is (= "hello" (get-body url)))
    (stop root-opts))
  (is (thrown? ConnectException (get-body url))))

(deftest stop-stops-the-requested-server
  (start hello)
  (is (= "hello" (get-body url)))
  (start {:port 8081} hello)
  (is (= "hello" (get-body url2)))
  (stop {:port 8081})
  (is (= "hello" (get-body url)))
  (is (thrown? ConnectException (get-body url2))))

(deftest stop-honors-server-in-opts
  (start hello)
  (is (= "hello" (get-body url)))
  (let [opts(start {:port 8081} hello)]
    (is (= "hello" (get-body url2)))
    (stop (select-keys opts [:server])))
  (is (= "hello" (get-body url)))
  (is (thrown? ConnectException (get-body url2))))

(deftest start-should-call-init
  (let [p (promise)]
    (start {:init #(deliver p 'init)} hello)
    (is (= 'init (deref p 10000 :fail)))))

(deftest stop-should-call-destroy
  (let [p (promise)]
    (start {:destroy #(deliver p 'destroy)} hello)
    (is (not (realized? p)))
    (stop)
    (is (= 'destroy (deref p 10000 :fail)))))

(deftest string-args-should-work
  (let [server (start {"port" "8042"} hello)]
    (is (= "hello" (get-body "http://localhost:8042")))))
