;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

(ns integs.cluster-test
  (:require [fntest.core :refer :all]
            [clojure.test :refer :all]
            [integs.cluster-help :refer (get-as-data stop start http-port mark)]
            [immutant.messaging :as msg]))

(def opts {:host "localhost", :remote-type :hornetq-wildfly,
           :username "testuser", :password "testuser"})

(use-fixtures :once
  (compose-fixtures
    (partial with-jboss #{:isolated :offset :domain})
    (with-deployment "ROOT.war" "." :profiles [:cluster :dev :test])))

(defmacro marktest [t & body]
  `(deftest ~t
     (let [v# (-> ~t var meta :name)]
       (mark "START" v#)
       ~@body
       (mark "FINISH" v#))))

(marktest bouncing-basic-web
  (is (= :pong (get-as-data "/ping" "server-one")))
  (is (= :pong (get-as-data "/ping" "server-two")))
  (stop "server-one")
  (is (thrown? java.net.ConnectException (get-as-data "/ping" "server-one")))
  (start "server-one")
  (is (= :pong (get-as-data "/ping" "server-one"))))

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
        response (fn [s]
                   (with-open [c (msg/context (assoc opts :port (http-port s)))]
                     (deref (msg/request (msg/queue "/queue/cache" :context c) :remote)
                       60000 {:node :timeout, :count 0})))]
    (mark (swap! responses conj (response "server-one")))
    (stop "server-one")
    (mark (swap! responses conj (response "server-two")))
    (is (= "master:server-two" (:node (last @responses))))
    (start "server-one")
    (mark (swap! responses conj (response "server-two")))
    (is (= "master:server-two" (:node (last @responses))))
    (stop "server-two")
    (mark (swap! responses conj (response "server-one")))
    (is (= "master:server-one" (:node (last @responses))))
    (start "server-two")
    ;; assert the job and distributed cache kept the count ascending
    ;; across restarts
    (is (apply < (map :count @responses)))))

(marktest publish-here-receive-there
  (with-open [ctx1 (msg/context (assoc opts :port (http-port "server-one")))
              ctx2 (msg/context (assoc opts :port (http-port "server-two")))]
    (let [q1 (msg/queue "/queue/cluster" :context ctx1)
          q2 (msg/queue "/queue/cluster" :context ctx2)]
      (dotimes [i 10]
        (msg/publish q1 i))
      (is (= (range 10) (repeatedly 10 #(msg/receive q2)))))))
