;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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
  (:use immutant.messaging)
  (:require [clj-http.client :as client]))

(def ham-queue "/queue/ham")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "apps/ring/basic-ring/"
                       :app-function "basic-ring.core/handler"
                       :context-path "/basic-ring"
                       :queues { ham-queue {"durable" false}}
                       }))

(deftest simple "it should work"
  (wait-for-destination #(publish ham-queue "testing"))
  (is (= (receive ham-queue :timeout 60000) "testing")))

