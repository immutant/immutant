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

(ns immutant.integs.msg.listen
  (:use fntest.core
        clojure.test
        immutant.messaging
        immutant.integs.integ-helper))

(def ham-queue "/queue/ham")
(def biscuit-queue ".queue.biscuit")
(def bam-queue "/queuebam")
(def hiscuit-queue "queue/hiscuit")
(def loader-queue "/queue/loader")
(def loader-result-queue "/queue/loader-result")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/queues"
                       }))

(deftest listen-should-work
  (publish biscuit-queue "foo")
  (is (= "FOO" (receive ham-queue :timeout 60000))))

(deftest remote-listen-should-work
  (listen bam-queue
          (fn [m]
            (publish hiscuit-queue m))
          :host "integ-app1.torquebox.org" :port 5445)
  (publish bam-queue "listen-up")
  (is (= (receive hiscuit-queue :timeout 60000) "listen-up")))

(deftest the-listener-should-be-using-the-deployment-classloader
  (publish loader-queue "whatevs")
  (is (re-seq deployment-class-loader-regex
              (receive loader-result-queue :timeout 60000))))

