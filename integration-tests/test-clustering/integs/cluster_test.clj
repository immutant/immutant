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

(ns integs.cluster-test
  (:require [fntest.core :refer :all]
            [clojure.test :refer :all]
            [environ.core :refer (env)]
            [integs.cluster-help :refer (get-as-data stop start mark) :as u]
            [immutant.messaging :as msg]))

(def opts {:host "localhost", :username "testuser", :password "testuser1!" :remote-type :hornetq-wildfly})
(def port u/http-port)
(def profiles [:cluster :dev :test])

(when (env :eap)
  (def opts (dissoc opts :remote-type))
  (def port u/messaging-port)
  (def profiles (conj profiles :eap-base)))

(use-fixtures :once
  (compose-fixtures
    (partial with-jboss #{:isolated :offset :domain})
    (with-deployment "ROOT.war" "." :profiles profiles)))

(defmacro marktest [t & body]
  `(deftest ~t
     (let [v# (-> ~t var meta :name)]
       (mark "START" v#)
       ~@body
       (mark "FINISH" v#))))

(marktest in-cluster
  (is (get-as-data "/in-cluster" "server-one"))
  (is (get-as-data "/in-cluster" "server-two")))

(marktest bouncing-basic-web
  (is (-> (get-as-data "/cache" "server-one") :count number?))
  (is (-> (get-as-data "/cache" "server-two") :count number?))
  (stop "server-one")
  (is (thrown? java.net.ConnectException (get-as-data "/cache" "server-one")))
  (start "server-one")
  (is (-> (get-as-data "/cache" "server-one") :count number? )))

(marktest session-replication
  (is (= 0 (get-as-data "/counter" "server-one")))
  (is (= 1 (get-as-data "/counter" "server-two")))
  (is (= 2 (get-as-data "/counter" "server-one")))
  (stop "server-one")
  (is (= 3 (get-as-data "/counter" "server-two")))
  (start "server-one")
  (is (= 4 (get-as-data "/counter" "server-one"))))

(marktest failover
  (let [responses (atom [])
        host1 (with-open [c (msg/context (assoc opts :port (port "server-one")))]
                (deref (msg/request (msg/queue "/queue/cache" :context c) :whatever)
                  60000 nil))
        host2 (-> #{"server-one" "server-two"} (disj host1) first)
        response (fn [s] (get-as-data "/cache" s))]
    (when-not host1
      (throw (IllegalStateException. "host1 is nil!")))
    (mark (swap! responses conj (response host1)))
    (stop host1)
    (mark (swap! responses conj (response host2)))
    (is (= host2 (:node (last @responses))))
    (start host1)
    (mark (swap! responses conj (response host2)))
    (stop host2)
    (mark (swap! responses conj (response host1)))
    (is (= host1 (:node (last @responses))))
    (start host2)
    ;; assert the job and distributed cache kept the count ascending
    ;; across restarts
    (is (apply < (map :count @responses)))))

(marktest publish-here-receive-there
  (with-open [ctx1 (msg/context (assoc opts :port (port "server-one")))
              ctx2 (msg/context (assoc opts :port (port "server-two")))]
    (let [q1 (msg/queue "/queue/cluster" :context ctx1)
          q2 (msg/queue "/queue/cluster" :context ctx2)
          received (atom #{})
          p (promise)
          c 100]
      (msg/listen q2 (fn [m]
                       (mark "GOT:" m)
                       (swap! received conj m)
                       (when (= c (count @received))
                         (deliver p :done))))
      (dotimes [i c]
        (msg/publish q1 i))
      (deref p 60000 nil)
      (is (= (set (range c)) @received)))))
