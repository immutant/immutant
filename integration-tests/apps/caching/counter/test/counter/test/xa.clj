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

(ns counter.test.xa
  (:use clojure.test)
  (:require [immutant.xa :as xa]
            [immutant.cache :as cache]
            [immutant.messaging :as msg]
            [immutant.util :as util]
            [clojure.tools.logging :as log]))

(def q "/queue/counter.test.xa")
(msg/start q :durable false)

(deftest listener-get-should-match-transactional-put
  (let [cache (cache/create "counter.test.xa")
        result (atom [])
        listener (msg/listen q #(swap! result conj (get cache %)))]
    (dotimes [x 500]
      (xa/transaction
       (cache/put cache x x)
       (msg/publish q x)))
    (util/wait-for #(= 500 (count @result)))
    (is (not-any? nil? @result))))

