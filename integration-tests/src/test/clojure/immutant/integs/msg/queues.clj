;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.integs.msg.queues
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/queues"
                       }))

(deftest timeout-should-return-nil
  (is (nil? (receive ham-queue :timeout 1))))

(deftest simple "it should work"
  (publish ham-queue "testing")
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest explicit-clojure-encoding-should-work
  (publish ham-queue "testing" :encoding :clojure)
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest explicit-json-encoding-should-work
  (publish ham-queue "testing" :encoding :json)
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest complex-json-encoding-should-work
  (let [message {:a "b" :c {:d "e"}}]
    (publish ham-queue message :encoding :json)
    (is (= (receive ham-queue :timeout 60000) message))))

(deftest lazy-message-seq
  (let [messages (message-seq ham-queue)]
    (doseq [i (range 4)] (publish ham-queue i))
    (is (= (range 4) (take 4 messages)))))

(deftest ttl-high
  (publish ham-queue "live!" :ttl 9999)
  (is (= "live!" (receive ham-queue :timeout 1000))))

(deftest ttl-low
  (publish ham-queue "die!" :ttl 1)
  (is (nil? (receive ham-queue :timeout 1000))))

(testing "remote connections"
  (deftest remote-publish-should-work
    (publish ham-queue "testing-remote" :host "integ-app1.torquebox.org" :port 5445)
    (is (= (receive ham-queue :timeout 60000) "testing-remote")))
  
  (deftest remote-receive-should-work
    (publish ham-queue "testing-remote")
    (is (= (receive ham-queue :timeout 60000 :host "integ-app1.torquebox.org" :port 5445)
           "testing-remote"))))

(deftest receive-with-decode-disabled-should-work
  (publish ham-queue "biscuit")
  (let [msg (receive ham-queue :decode? false)]
    (is (isa? (class msg) javax.jms.Message))))
