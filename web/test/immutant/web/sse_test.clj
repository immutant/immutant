;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.sse-test
  (:require [clojure.test :refer :all]
            [immutant.web.sse :refer :all]
            [clojure.string :refer (split-lines)]))

(deftest format-string
  (is (= "data:foo\n"
        (event->str "foo"))))

(deftest format-collection
  (is (= "data:0\ndata:1\ndata:2\n"
        (event->str [0 1 2])
        (event->str (range 3))
        (event->str '("0" "1" "2")))))

(deftest format-map
  (let [result (event->str {:data "foo", :event "bar", :id 42, :retry 1000})
        sorted (sort (split-lines result))]
    (is (.endsWith result "\n"))
    (is (= ["data:foo" "event:bar" "id:42" "retry:1000"] sorted))))

(deftest format-collection-in-map
  (is (= "data:0\ndata:1\ndata:2\n"
        (event->str {:data (range 3)}))))
