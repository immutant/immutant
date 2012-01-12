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

(ns immutant.integs.msg.priority
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def ham-queue "/queue/ham")
(def biscuit-queue "/queue/biscuit")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "apps/messaging/basic"
                       }))

(deftest default-priority-should-be-fifo
  (let [messages (message-seq ham-queue)]
    (dotimes [x 10] (publish ham-queue x))
    (is (= (range 10) (take 10 messages)))))

(deftest prioritize-by-integer
  (let [messages (message-seq ham-queue)]
    (dotimes [x 10] (publish ham-queue x :priority x))
    (is (= (reverse (range 10)) (take 10 messages)))))

(deftest prioritize-by-keyword
  (let [messages (message-seq ham-queue)
        labels [:low :normal :high :critical]
        size (count labels)]
    (dotimes [x size] (publish ham-queue x :priority (labels x)))
    (is (= (reverse (range size)) (take size messages)))))

