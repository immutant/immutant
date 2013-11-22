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

(ns immutant.cluster.messaging
  (:use fntest.core
        clojure.test
        [immutant.cluster.helper :only [stop start messaging-port]])
  (:require [immutant.messaging :as msg]))

(use-fixtures :once (with-deployment *file*
                      {:root "target/apps/cluster/"}))

(deftest publish-here-receive-there
  (let [q "/queue/cluster"
        p1 (messaging-port "server-one")
        p2 (messaging-port "server-two")]
    (dotimes [i 10]
      (msg/publish q i, :host "localhost", :port p1))
    (is (= (range 10) (sort (take 10 (msg/message-seq q, :host "localhost", :port p2)))))))

(deftest daemon-failover
  (let [q "/queue/nodename"
        p1 (messaging-port "server-one")
        p2 (messaging-port "server-two")]
    (stop "server-one")
    (is (= "master:server-two" @(msg/request q nil, :port p2, :host "localhost")))
    (start "server-one")
    (is (= "master:server-two" @(msg/request q nil, :port p2, :host "localhost")))
    (is (= "master:server-two" @(msg/request q nil, :port p1, :host "localhost")))
    (stop "server-two")
    (is (= "master:server-one" @(msg/request q nil, :port p1, :host "localhost")))
    (start "server-two")))
