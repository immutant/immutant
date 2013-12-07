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

(ns immutant.cluster.ha
  (:use fntest.core
        clojure.test
        [immutant.cluster.helper :only [stop start messaging-port]])
  (:require [immutant.messaging :as msg]))

(use-fixtures :once (with-deployment *file*
                      {:root "target/apps/cluster/"}))

(def responses (atom []))

(defn response [queue port]
  (swap! responses conj
    (deref (msg/request queue :remote, :port port, :host "localhost")
      10000 {:node :timeout, :count 0})))

(deftest failover
  (let [q "/queue/cache"
        p1 (messaging-port "server-one")
        p2 (messaging-port "server-two")]
    (println (response q p1))
    (stop "server-one")
    (println (response q p2))
    (is (= "master:server-two" (:node (last @responses))))
    (start "server-one")
    (println (response q p1))
    (is (= "master:server-two" (:node (last @responses))))
    (stop "server-two")
    (println (response q p1))
    (is (= "master:server-one" (:node (last @responses))))
    (start "server-two")
    
    ;; assert the job and distributed cache kept the count ascending
    ;; across restarts
    (is (apply < (map :count @responses)))))
