;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(ns immutant.codecs.fressian-test
  (:require [immutant.codecs          :refer :all]
            [immutant.codecs.fressian :refer :all]
            [clojure.test             :refer :all]
            [clojure.data.fressian    :as fressian])
  (:import [java.awt Point]
           [org.fressian.handlers WriteHandler ReadHandler]))

(defn test-codec [object encoding]
  (let [encoded (encode object encoding)]
    (is (= object (decode encoded encoding)))))

(use-fixtures :once
  (fn [f]
    (register-fressian-codec)
    (register-fressian-codec
      :name :fressian-point
      :content-type "application/fressian+point"
      :read-handlers (-> (merge
                           {"point" (reify ReadHandler
                                      (read [_ reader tag component-count]
                                        (Point. (.readInt reader) (.readInt reader))))}
                           fressian/clojure-read-handlers)
                       fressian/associative-lookup)
      :write-handlers (-> (merge
                            {java.awt.Point {"point" (reify WriteHandler
                                                       (write [_ writer point]
                                                         (.writeTag writer "point" 2)
                                                         (.writeInt writer (.x point))
                                                         (.writeInt writer (.y point))))}}
                            fressian/clojure-write-handlers)
                        fressian/associative-lookup
                        fressian/inheritance-lookup))
    (f)))

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

(deftest fressian-decode-nil
  (test-codec nil :fressian))

(deftest custom-handlers
  (test-codec {:point (Point. 0 1)} :fressian-point))

(deftest fressian-decode-errors-should-throw-ex-info
  (let [data (byte-array [0xb0])]
    (try
      (decode data :fressian)
      (is false)
      (catch clojure.lang.ExceptionInfo e
        (is (= {:data data
                :type :decode-exception}
              (ex-data e)))))))
