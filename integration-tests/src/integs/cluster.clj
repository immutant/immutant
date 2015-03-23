;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns integs.cluster
  (:require [immutant.messaging  :as m]
            [immutant.caching    :as c]
            [immutant.scheduling :as s]
            [immutant.web        :as w]
            [immutant.web.middleware :refer (wrap-session)]
            [immutant.daemons        :refer (singleton-daemon)]
            [ring.util.response      :refer (response)]))

(def nodename (System/getProperty "jboss.node.name"))
(def cache (c/cache "cluster-test", :transactional? true, :locking :pessimistic))

(defn update-cache []
  (println "UPDATE:" (into {} cache))
  (.put cache :node nodename)
  (c/swap-in! cache :count (fnil inc -1)))

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str count))
      (assoc :session session))))

(defn -main [& _]
  (let [q (m/queue "/queue/cache", :durable? false)
        resp  (atom nil)
        start #(reset! resp (m/respond q (fn [_]
                                           (let [c (into {} cache)]
                                             (println "RESPONSE:" c)
                                             c))))
        stop  #(m/stop @resp)]
    (singleton-daemon "cache-status" start stop))
  (s/schedule update-cache :id "cache-updater" :every :second)
  (m/queue "/queue/cluster", :durable? false)
  (w/run (-> #'counter wrap-session) :path "/counter")
  (w/run (fn [_] (response ":pong")) :path "/ping"))
