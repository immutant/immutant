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

(ns immutant.daemons-test
  (:require [immutant.daemons :refer :all]
            [clojure.test  :refer :all])
  (:import org.projectodd.wunderboss.WunderBoss))

(deftest singleton-should-work-outside-of-the-container
  (let [started-p (promise)
        thread-a (atom nil)
        stopped-p (promise)]
    (singleton-daemon :foo
      (fn []
        (reset! thread-a (.getName (Thread/currentThread)))
        (deliver started-p :started))
      #(deliver stopped-p :stopped))
    (is (= :started (deref started-p 1000 :failure)))
    ;; confirm we're on the right thread
    (is (re-find #"daemon-thread\[foo\]" @thread-a))
    (WunderBoss/shutdownAndReset)
    ;; confirm stop fn is called
    (is (= :stopped (deref stopped-p 1000 :failure)))))
