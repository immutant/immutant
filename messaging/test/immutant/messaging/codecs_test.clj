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
       (body [_] body))))

(deftest decode-with-metadata-should-work
  (= {:foo :bar}
    (meta (decode-with-metadata (make-message "{}" "application/edn" {:foo :bar}))))
  (= nil
    (meta (decode-with-metadata (make-message "0" "application/edn" {:foo :bar})))))
