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

(ns immutant.messaging.codecs-test
  (:require [immutant.messaging.codecs :refer :all]
            [immutant.codecs           :as core]
            [clojure.test              :refer :all])
  (:import org.projectodd.wunderboss.messaging.Message))

(defn make-message
  ([body content-type]
     (make-message body content-type {}))
  ([body content-type properties]
     (reify Message
       (contentType [_]
         content-type)
       (properties [_] properties)
       (body [_ _] body))))

(defn test-codec [message encoding]
  (let [encoded (encode message encoding)]
    (is (= message
          (decode (apply make-message encoded))))))

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
        encoded (apply make-message (encode message :json))]
    (is (= message (decode encoded)))
    (is (.contains (.body encoded String) "\"a\":\"b\""))
    (is (.contains (.body encoded String) "\"c\":[1,2,3,{\"foo\":42}]"))))

(deftest text
  (test-codec "ham biscuit" :text))

(deftest decode-with-metadata-should-work
  (= {:foo :bar}
    (meta (decode-with-metadata (make-message "{}" "application/edn" {:foo :bar}))))
  (= nil
    (meta (decode-with-metadata (make-message "0" "application/edn" {:foo :bar})))))
