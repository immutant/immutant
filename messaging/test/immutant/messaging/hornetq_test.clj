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

(ns immutant.messaging.hornetq-test
  (:require [immutant.messaging :as msg]
            [immutant.messaging.hornetq :refer :all]
            [clojure.test :refer :all]))

(deftest normalize-destination-match-works
  (are [exp given] (= exp (#'immutant.messaging.hornetq/normalize-destination-match given))
       "#"                   "#"
       "jms.queue.#"         "jms.queue.#"
       "jms.queue.foo.queue" (msg/queue "foo.queue")
       "jms.queue.foo-bar"   (msg/queue "foo-bar"))
  (doseq [match ["jms.#" "*" "foo"]]
    (is (thrown? IllegalArgumentException
          (#'immutant.messaging.hornetq/normalize-destination-match match)))))

(deftest set-companion-options-works
  (are [exp given] (= exp (#'immutant.messaging.hornetq/set-companion-options given))
       {:last-value-queue true, :address-full-message-policy :drop}
       {:last-value-queue true}

       {:last-value-queue true, :address-full-message-policy :block}
       {:last-value-queue true, :address-full-message-policy :block}

       {:last-value-queue true, :address-full-message-policy :drop}
       {:last-value-queue true, :address-full-message-policy :page}

       {:last-value-queue false, :address-full-message-policy :page}
       {:last-value-queue false, :address-full-message-policy :page}))
