;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.test.codecs
  (:use [immutant.codecs])
  (:use [clojure.test]))

(defn test-codec [object encoding]
  (is (= object (decode (encode object encoding) encoding))))

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

(deftest json-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :json))

(deftest clojure-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :clojure))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (encode message :json)]
    (is (= encoded "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}"))))

(deftest text
  (test-codec "ham biscuit" :text))

(deftest decode-nil
  (are [x] (nil? x)
       (decode nil)
       (decode nil :clojure)
       (decode nil :json)
       (decode nil :text)))

(deftest decode-list
  (is (= '(1 2 3) (decode "(1 2 3)"))))