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

(ns immutant.integs.msg.request-response
  (:use fntest.core
        clojure.test
        immutant.messaging))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")
(def oddball-queue (as-queue "oddball"))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/request-response"
                       }))

(deftest request-and-respond-should-both-work
  (is (= "BISCUIT" @(request ham-queue "biscuit" :timeout 2000))))

(deftest request-and-respond-with-a-selector-should-work
  (is (= "BISCUIT" @(request biscuit-queue "biscuit"
                             :timeout 2000
                             :properties {"worker" "upper"})))
  (is (= "ham" @(request biscuit-queue "HAM"
                         :timeout 2000
                         :properties {"worker" "lower"}))))

(deftest request-and-respond-with-as-queue-should-both-work
  (is (= "BISCUIT" @(request oddball-queue "biscuit" :timeout 2000))))
