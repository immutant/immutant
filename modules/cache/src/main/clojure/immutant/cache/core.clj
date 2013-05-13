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

(ns ^{:no-doc true} immutant.cache.core
    (:require [immutant.registry :as registry]
              [clojure.java.io :as io]
              [clojure.tools.logging :as log])
    (:import [org.infinispan.configuration.cache ConfigurationBuilder VersioningScheme CacheMode]
             [org.infinispan.transaction TransactionMode LockingMode]
             org.infinispan.eviction.EvictionStrategy
             org.infinispan.manager.DefaultCacheManager
             org.infinispan.transaction.lookup.GenericTransactionManagerLookup
             org.infinispan.util.concurrent.IsolationLevel))

(def service (registry/get org.projectodd.polyglot.cache.as.CacheService/CACHE))
(def manager (delay (or (and service (.getCacheContainer service)) (DefaultCacheManager.))))

(defn cache-mode
  [{:keys [mode sync]}]
  (cond
    (= :invalidated mode) (if sync CacheMode/INVALIDATION_SYNC CacheMode/INVALIDATION_ASYNC)
    (= :distributed mode) (if sync CacheMode/DIST_SYNC CacheMode/DIST_ASYNC)
    (= :replicated mode) (if sync CacheMode/REPL_SYNC CacheMode/REPL_ASYNC)
    :else CacheMode/LOCAL))

(defn eviction-strategy
  [mode]
  (cond
   (= :lru mode) EvictionStrategy/LRU
   (= :lirs mode) EvictionStrategy/LIRS
   (= :unordered mode) EvictionStrategy/UNORDERED
   :else (throw (IllegalArgumentException. (str "Invalid eviction strategy: " mode)))))

(defn set-optimistic-locking!
  [builder]
  (.. builder transaction
      (lockingMode LockingMode/OPTIMISTIC))
  (.. builder versioning
      (enabled true)
      (scheme VersioningScheme/SIMPLE))
  (.. builder locking
      (isolationLevel IsolationLevel/REPEATABLE_READ)
      (writeSkewCheck true))
  builder)

(defn set-pessimistic-locking!
  [builder]
  (.. builder transaction
      (lockingMode LockingMode/PESSIMISTIC))
  builder)

(defn build-config
  [{:keys [locking template persist max-entries eviction] :as opts}]
  (let [builder (ConfigurationBuilder.)]
    (if template (.read builder template))
    (.classLoader builder (.getContextClassLoader (Thread/currentThread)))
    (.. builder transaction
        (transactionManagerLookup (GenericTransactionManagerLookup.))
        (transactionMode TransactionMode/TRANSACTIONAL))
    (.. builder clustering
        (cacheMode (cache-mode opts)))
    (if persist
      (let [store (.. builder loaders addFileCacheStore)]
        (if (.exists (io/file (str persist)))
          (.. store (location persist)))))
    (if max-entries
      (.. builder eviction (maxEntries max-entries)))
    (if eviction
      (.. builder eviction (strategy (eviction-strategy eviction))))
    (cond
      (= locking :pessimistic) (set-pessimistic-locking! builder)
      (= locking :optimistic) (set-optimistic-locking! builder)
      locking (throw (IllegalArgumentException. (str "Invalid locking mode: " locking))))
    (.build builder)))

(defn default-mode []
  (if (and service (.isClustered service))
    :distributed
    :local))

(defn get-cache
  "Returns the named cache if it exists, otherwise nil"
  [name]
  (if (.isRunning @manager name)
    (.getCache @manager name)))
  
(defn configure-cache
  "Defaults to :distributed with :sync=true for a clustered cache, otherwise :local"
  [name opts]
  (let [config (merge {:mode (default-mode), :sync true} opts)]
    (log/info "Configuring cache" name "as"
              (select-keys config [:mode :sync :locking :persist :max-entries :eviction]))
    (.defineConfiguration @manager name (build-config config))
    (when-let [cache (get-cache name)]
      (.stop cache)
      (.start cache))
    (.getCache @manager name)))

(defn lifespan-params [{:keys [ttl idle units] :or {ttl -1 idle -1 units :seconds}}]
  (let [u (.toUpperCase (name units))
        tu (java.util.concurrent.TimeUnit/valueOf u)]
    (list ttl tu idle tu)))

(defmacro expire [form]
  `(apply (fn [e# eu# i# iu#]
            (~@(drop-last form) e# eu# i# iu#))
          (lifespan-params ~(last form))))
