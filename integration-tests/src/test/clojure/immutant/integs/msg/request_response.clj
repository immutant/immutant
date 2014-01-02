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

(ns immutant.integs.msg.request-response
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [remote]]
        immutant.messaging))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")
(def oddball-queue (as-queue "oddball"))
(def sleepy-queue "queue.sleeper")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/request-response"
                       }))

(deftest request-and-respond-should-both-work
  (is (= "BISCUIT" @(remote request ham-queue "biscuit"))))

(deftest request-and-respond-with-a-selector-should-work
  (is (= "BISCUIT" @(remote request biscuit-queue "biscuit"
                             :properties {"worker" "upper"})))
  (is (= "ham" @(remote request biscuit-queue "HAM"
                         :properties {"worker" "lower"}))))

(deftest request-and-respond-with-as-queue-should-both-work
  (is (= "BISCUIT" @(remote request oddball-queue "biscuit"))))

(deftest request-with-a-deref-timeout-should-work
  (is (= 100 (deref (remote request sleepy-queue 100) 1000 nil))))

(deftest realized?-should-work
  (let [response (remote request sleepy-queue 1000)]
    (is (not (realized? response)))
    (is (= 1000 (time @response)))
    (is (realized? response))))
