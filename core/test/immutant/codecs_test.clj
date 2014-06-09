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

(deftest clojure-date
  (test-codec (java.util.Date.) :clojure))

(deftest clojure-date-inside-hash
  (test-codec {:date (java.util.Date.)} :clojure))

(deftest clojure-date-inside-vector
  (test-codec [(java.util.Date.)] :clojure))

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

(deftest text
  (test-codec "ham biscuit" :text))

(deftest none
  (test-codec "ham biscuit" :none))

(deftest decode-nil
  (are [x] (nil? x)
       (decode (encode nil))
       (decode (encode nil :clojure) :clojure)
       (decode (encode nil :edn) :edn)
       (decode (encode nil :json) :json)
       (decode (encode nil :none) :none)))

(deftest decode-list
  (is (= '(1 2 3) (decode (encode '(1 2 3))))))

(deftest default-content-type->encoding
  (is (= :text (content-type->encoding "text/plain")))
  (is (thrown? IllegalArgumentException
        (content-type->encoding "invalid"))))

(deftest default-encoding->content-type
  (is (=  "text/plain" (encoding->content-type :text)))
  (is (=  "text/plain" (encoding->content-type "text")))
  (is (thrown? IllegalArgumentException
        (encoding->content-type :invalid))))
