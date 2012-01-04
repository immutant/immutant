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

(ns immutant.integs.messaging
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "apps/ring/basic-ring/"
                       :init "basic-ring.core/init-messaging"
                       }))

(deftest simple "it should work"
  (wait-for-destination #(publish ham-queue "testing"))
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest explicit-clojure-encoding-should-work
  (wait-for-destination #(publish ham-queue "testing" :encoding :clojure))
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest explicit-json-encoding-should-work
  (wait-for-destination #(publish ham-queue "testing" :encoding :json))
  (is (= (receive ham-queue :timeout 60000) "testing")))

(deftest complex-json-encoding-should-work
  (let [message {:a "b" :c {:d "e"}}]
    (wait-for-destination #(publish ham-queue message :encoding :json))
    (is (= (receive ham-queue :timeout 60000) message))))

(deftest trigger-processor-to-log-something
  (wait-for-destination #(publish biscuit-queue "foo"))
  (is (= "FOO" (receive ham-queue :timeout 60000))))
