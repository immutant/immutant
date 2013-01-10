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

(ns immutant.integs.msg.listen
  (:use fntest.core
        clojure.test
        immutant.messaging
        immutant.integs.integ-helper)
  (:require [clojure.java.jmx :as jmx]))

(def ham-queue "/queue/ham")
(def biscuit-queue ".queue.biscuit")
(def bam-queue "/queuebam")
(def hiscuit-queue "queue/hiscuit")
(def loader-queue "/queue/loader")
(def loader-result-queue "/queue/loader-result")
(def addboll-queue (as-queue "addboll"))
(def odd-response-queue (as-queue "odd-response"))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/queues"
                       }))

(deftest listen-should-work
  (publish biscuit-queue "foo")
  (is (= "FOO" (receive ham-queue :timeout 60000))))

(deftest listen-with-as-queue-should-work
  (publish (as-queue "oddball") "BACON")
  (is (= "bacon" (receive ham-queue :timeout 60000))))

(deftest remote-listen-should-work
  (let [listener (listen bam-queue
                          (fn [m]
                            (publish hiscuit-queue m))
                          :host "integ-app1.torquebox.org" :port 5445)]
    (publish bam-queue "listen-up")
    (is (= (receive hiscuit-queue :timeout 60000) "listen-up"))
    (unlisten listener)))

(deftest remote-listen-with-as-queue-should-work
  (let [listener (listen addboll-queue
                          (fn [m]
                            (publish odd-response-queue m))
                          :host "integ-app1.torquebox.org" :port 5445)]
    (publish addboll-queue "ahoyhoy")
    (is (= (receive odd-response-queue :timeout 60000) "ahoyhoy"))
    (unlisten listener)))

(deftest the-listener-should-be-using-the-deployment-classloader
  (publish loader-queue "whatevs")
  (is (re-seq deployment-class-loader-regex
              (receive loader-result-queue :timeout 60000))))

(deftest a-listener-should-have-a-findable-mbean
  (jmx/with-connection {:url "service:jmx:remoting-jmx://127.0.0.1:9999"}
    (is (jmx/mbean "immutant.messaging:name=.queue.biscuit.,app=listen"))))

(let [request-queue "queue.listen-id.request"
      response-queue "queue.listen-id.response"]
  (deftest listen-on-a-queue-should-be-idempotent
    (publish request-queue :whatever)
    (is (= :old-listener (receive response-queue)))
    (publish request-queue :whatever)
    (is (= :new-listener (receive response-queue)))))
