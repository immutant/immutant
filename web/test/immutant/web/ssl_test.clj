;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.ssl-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [immutant.web.ssl])
  (:import [java.security KeyStore]))

;;; test private function
(def load-keystore #'immutant.web.ssl/load-keystore)

(deftest keystore-as-string
  (is (instance? KeyStore (load-keystore "dev-resources/keystore.jks" "password"))))

(deftest keystore-as-file
  (is (instance? KeyStore (load-keystore (io/file "dev-resources/keystore.jks") "password"))))

(deftest keystore-as-keystore
  (is (instance? KeyStore (load-keystore (load-keystore (io/file "dev-resources/keystore.jks") "password") "doesn't matter"))))
