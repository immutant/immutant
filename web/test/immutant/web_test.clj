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
  (import clojure.lang.ExceptionInfo))

(def url "http://localhost:8080/")

(deftest run-should-var-quote
  (run hello)
  (is (= "hello" (get-body url)))
  (with-redefs [hello (handler "howdy")]
    (is (= "howdy" (get-body url)))))

(deftest mount-unquoted-handler
  (mount hello)
  (is (= "hello") (get-body url))
  (with-redefs [hello (handler "hi")]
    (is (= "hello" (get-body url)))))

(deftest mount-quoted-handler
  (mount #'hello)
  (is (= "hello") (get-body url))
  (with-redefs [hello (handler "hi")]
    (is (= "hi" (get-body url)))))

(deftest run-non-var
  (let [h hello]
    (run h)
    (is (= "hello" (get-body url)))))

(deftest run-non-var-that-shadows
  (let [handler hello]
    (run handler)
    (is (= "hello" (get-body url)))))

(deftest mount-pedestal-service
  (mount pedestal/servlet)
  (is (= "Hello World!" (get-body url))))

(deftest unmount-yields-404
  (run hello)
  (is (= "hello" (get-body url)))
  (unmount)
  (let [result (is (thrown? ExceptionInfo (get-body url)))]
    (is (= 404 (-> result ex-data :object :status)))))

(deftest mount-should-call-init
  (let [p (promise)]
    (run hello {:init #(deliver p 'init)})
    (is (= 'init @p))))

(deftest unmount-should-call-destroy
  (let [p (promise)]
    (run hello {:destroy #(deliver p 'destroy)})
    (is (not (realized? p)))
    (unmount)
    (is (= 'destroy @p))))

(deftest string-args-should-work
  (run hello {"port" "8042" "name" "ralph"})
  (is (= "hello" (get-body "http://localhost:8042")))
  (.stop (server {:name "ralph"})))
