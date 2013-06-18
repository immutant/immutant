;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns immutant.integs.msg.destination-in-multiple-apps
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [get-as-data]])
  (:require [immutant.messaging :as msg]))

(use-fixtures :once 
              (with-deployments {"q1"
                                 {
                                   :root "target/apps/messaging/queues/"
                                   :context-path "/q1"
                                 }
                                 "q2"
                                 {
                                   :root "target/apps/messaging/queues/"
                                   :context-path "/q2"
                                 }
                                 "t1"
                                 {
                                   :root "target/apps/messaging/topics/"
                                   :context-path "/t1"
                                 }
                                 "t2"
                                 {
                                   :root "target/apps/messaging/topics/"
                                   :context-path "/t2"
                                 }
                                 }))

(deftest both-queue-apps-should-be-up
  (is (= :success (get-as-data "/q1")))
  (is (= :ham (deref (msg/request "queue.echo" :ham) 15000 nil)))
  (is (= :success (get-as-data "/q1")))

  (is (= :success (get-as-data "/q2")))
  (is (= :ham (deref (msg/request "queue.echo" :ham) 15000 nil)))
  (is (= :success (get-as-data "/q2"))))

(deftest both-topic-apps-should-be-up
  (is (= :success (get-as-data "/t1")))
  (msg/publish "topic.echo" :ham)
  (is (= :ham (msg/receive "queue.result" :timeout 15000)))
  (is (= :success (get-as-data "/t1")))

  (is (= :success (get-as-data "/t2")))
  (msg/publish "topic.echo" :ham)
  (is (= :ham (msg/receive "queue.result" :timeout 15000)))
  (is (= :success (get-as-data "/t2"))))

(deftest verify-in-container-reconfigure-tests
  (is (test-in-container "reconfigure" "target/apps/messaging/queues/")))


