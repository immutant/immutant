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

(ns immutant.codecs-test
  (:require [immutant.codecs :refer :all]
            [clojure.test    :refer :all]))

(defn test-codec [object encoding]
  (let [encoded (encode object encoding)]
    (is (= object (decode encoded encoding)))))

(deftest json-string
  (test-codec "a random text message" :json))

(deftest clojure-string
  (test-codec "a simple text message" :clojure))

(deftest edn-string
  (test-codec "a simple text message" :edn))

(deftest edn-date
  (test-codec (java.util.Date.) :edn))

(deftest edn-date-inside-hash
  (test-codec {:date (java.util.Date.)} :edn))

(deftest edn-date-inside-vector
  (test-codec [(java.util.Date.)] :edn))

(deftest json-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :json))

(deftest clojure-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :clojure))

(deftest none-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :none))

(deftest edn-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :edn))

(deftest fressian-string
  (test-codec "a simple text message" :fressian))

(deftest fressian-date
  (test-codec (java.util.Date.) :fressian))

(deftest fressian-date-inside-hash
  (test-codec {:date (java.util.Date.)} :fressian))

(deftest fressian-date-inside-vector
  (test-codec [(java.util.Date.)] :fressian))

(deftest fressian-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :fressian))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (encode message :json)]
    (is (= message (decode encoded :json)))
    (is (= message (decode "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}" :json)))))

(deftest none
  (test-codec "ham biscuit" :none))

(deftest decode-nil
  (doseq [codec (.codecs codecs)]
    (is (nil? (.decode codec (.encode codec nil))))))

(defrecord ARecord [x])

(deftest records-via-clojure-encoding
  (test-codec (->ARecord :x) :clojure))

(deftest records-via-none-encoding
  (test-codec (->ARecord :x) :none))
