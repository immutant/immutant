;; Copyright 2014 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc immutant.caching.options
  "Spruces up some of the option values"
  (:require [immutant.coercions :refer (as-period)])
  (:import [org.projectodd.wunderboss.caching.notifications Handler
            Listener$CacheEntriesEvicted Listener$CacheEntryActivated
            Listener$CacheEntryCreated Listener$CacheEntryInvalidated
            Listener$CacheEntryLoaded Listener$CacheEntryModified
            Listener$CacheEntryPassivated Listener$CacheEntryRemoved
            Listener$CacheEntryVisited Listener$DataRehashed
            Listener$TopologyChanged Listener$TransactionCompleted
            Listener$TransactionRegistered]))

(defn period-converter [key]
  (fn [m] (if (get m key)
           (update-in m [key] as-period)
           m)))

(defn keyword-converter [key]
  (fn [m] (if (get m key)
           (update-in m [key] #(.replace (name %) \- \_))
           m)))

(def wash (comp
            (period-converter :idle)
            (period-converter :ttl)
            (keyword-converter :mode)
            (keyword-converter :eviction)
            (keyword-converter :locking)))

(defn listener
  [f type]
  (let [handler (reify Handler (handle [_ event] (f event)))]
    (case type
      :cache-entries-evicted   (Listener$CacheEntriesEvicted. handler)
      :cache-entry-activated   (Listener$CacheEntryActivated. handler)
      :cache-entry-created     (Listener$CacheEntryCreated. handler)
      :cache-entry-invalidated (Listener$CacheEntryInvalidated. handler)
      :cache-entry-loaded      (Listener$CacheEntryLoaded. handler)
      :cache-entry-modified    (Listener$CacheEntryModified. handler)
      :cache-entry-passivated  (Listener$CacheEntryPassivated. handler)
      :cache-entry-removed     (Listener$CacheEntryRemoved. handler)
      :cache-entry-visited     (Listener$CacheEntryVisited. handler)
      :data-rehashed           (Listener$DataRehashed. handler)
      :topology-changed        (Listener$TopologyChanged. handler)
      :transaction-completed   (Listener$TransactionCompleted. handler)
      :transaction-registered  (Listener$TransactionRegistered. handler))))
