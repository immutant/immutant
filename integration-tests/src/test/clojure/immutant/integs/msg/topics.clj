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

(ns immutant.integs.msg.topics
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def gravy "/topic/gravy")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "apps/messaging/topics"
                       }))

(deftest publish-to-multiple-subscribers
  (let [msgs (pmap (fn [_] (receive gravy)) (range 10))]
    (Thread/sleep 1000)                 ; give subscribers some time 
    (publish gravy "biscuit")
    (is (= 10 (count msgs)))
    (is (every? (partial = "biscuit") msgs))))