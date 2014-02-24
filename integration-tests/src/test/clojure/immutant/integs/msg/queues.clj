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

(ns immutant.integs.msg.queues
  (:use fntest.core
        clojure.test
        [immutant.messaging.core :only [delayed]]
        [immutant.integs.integ-helper :only [remote]])
  (:require [immutant.messaging :as msg]))

(def ham-queue "/queue/ham")
(def biscuit-queue ".queue.biscuit")
(def oddball-queue (msg/as-queue "oddball"))
(def addboll-queue (msg/as-queue "addboll"))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/queues"
                       }))

(def publish (partial remote msg/publish))

(def receive (partial remote msg/receive))

(def message-seq (partial remote msg/message-seq))

(defn delayed-receive [q]
  (delayed #(receive q :timeout %1 :timeout-val %2)))

(deftest timeout-should-return-nil
  (is (nil? (receive ham-queue :timeout 1))))

(deftest timeout-with-val-should-return-val
  (is (= :val (receive ham-queue :timeout 1 :timeout-val :val))))

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

(testing "delayed-receive"
  (deftest deref-should-work
    (publish ham-queue :hi)
    (is (= :hi @(delayed-receive ham-queue))))

  (deftest deref-should-cache
    (publish ham-queue :hi)
    (let [result (delayed-receive ham-queue)]
      (is (= :hi @result))
      (is (= :hi @result))))

  (deftest deref-with-a-timeout-should-work
    (publish ham-queue :hi)
    (is (= :hi (deref (delayed-receive ham-queue) 1000 nil))))

  (deftest deref-with-a-timeout-and-default-should-work
    (is (= :hello (deref (delayed-receive ham-queue) 1 :hello))))
  
  (deftest realized?-should-work
    (let [result (delayed-receive ham-queue)]
      (is (not (realized? result)))
      (publish ham-queue :hi)
      (deref result 1000 nil)
      (is (realized? result))))

  (deftest force-should-work
    (publish ham-queue :ahoyhoy)
    (is (= :ahoyhoy (force (delayed-receive ham-queue))))))

(testing "remote connections"
  (deftest remote-publish-should-work
    (publish ham-queue "testing-remote" :host "integ-app1.torquebox.org")
    (is (= (receive ham-queue :timeout 60000) "testing-remote")))
  
  (deftest remote-receive-should-work
    (publish ham-queue "testing-remote")
    (is (= (receive ham-queue :timeout 60000 :host "integ-app1.torquebox.org")
           "testing-remote")))

  (deftest remote-publish-with-as-queue-should-work
    (publish oddball-queue "testing-remote" :host "integ-app1.torquebox.org")
    (is (= (receive ham-queue :timeout 60000) "testing-remote")))
  
  (deftest remote-receive-with-as-queue-should-work
    (publish addboll-queue "testing-remote")
    (is (= (receive addboll-queue :timeout 60000 :host "integ-app1.torquebox.org")
           "testing-remote"))))

(deftest receive-with-decode-disabled-should-work
  (publish ham-queue "biscuit")
  (let [msg (receive ham-queue :decode? false)]
    (is (isa? (class msg) javax.jms.Message))))
