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

(ns immutant.util-test
  (:require [immutant.util :refer :all]
            [clojure.test  :refer :all]))

(deftest messaging-remote-port-should-work
  (testing "the default"
    (is (= 5445 (messaging-remoting-port))))

  (testing "honor the sysprop"
    (let [props (System/getProperties)]
      (System/setProperty "hornetq.netty.port" "1234")
      (is (= 1234 (messaging-remoting-port)))
      (System/setProperties props)))

  (testing "inside wildfly"
    (with-redefs [immutant.internal.util/try-resolve (constantly (constantly 8080))]
      (is (= 8080 (messaging-remoting-port))))))
