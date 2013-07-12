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
              [immutant.util :as util]
              [clojure.java.io :as io]
              [clojure.tools.logging :as log])
    (:import [org.infinispan.configuration.cache ConfigurationBuilder VersioningScheme CacheMode]
             [org.infinispan.transaction TransactionMode LockingMode]
             org.infinispan.eviction.EvictionStrategy
             org.infinispan.manager.DefaultCacheManager
             org.infinispan.transaction.lookup.GenericTransactionManagerLookup
             org.infinispan.util.concurrent.IsolationLevel
             java.util.concurrent.TimeUnit))

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
      (lockingMode LockingMode/OPTIMISTIC)
      (useSynchronization false))
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
      (lockingMode LockingMode/PESSIMISTIC)
      (useSynchronization false))
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

(defn default-mode [opts]
  (if (and service (.isClustered service))
    (merge {:mode :distributed} opts)
    (do
      (if-not (= :local (:mode opts :local))
        (log/warn "Cache replication only supported when clustered"))
      (assoc opts :mode :local))))

(defn get-cache
  "Returns the named cache if it exists, otherwise nil"
  [name]
  (if (.isRunning @manager name)
    (.getCache @manager name)))
  
(defn configure-cache
  "Defaults to :distributed with :sync=true for a clustered cache, otherwise :local"
  [name opts]
  (let [default (.getDefaultCacheConfiguration @manager)
        settings (merge {:template default, :sync true} (default-mode opts))
        config (build-config settings)]
    (log/info (str "Configuring cache [" name "] as "
                   (select-keys settings [:mode :sync :locking :persist :max-entries :eviction])))
    (log/debug (str "Infinispan options for cache [" name "]: " config))
    (.defineConfiguration @manager name config)
    (when-let [cache (get-cache name)]
      (.stop cache)
      (.start cache))
    (let [cache (.getCache @manager name)]
      (util/at-exit #(.stop cache))
      cache)))

(defn time-unit
  [kw]
  (let [n (name kw)
        n (if (.endsWith n "s") n (str n "s"))]
    (TimeUnit/valueOf (.toUpperCase n))))

(defn duration
  [v units]
  (if (coll? v)
    [(first v) (time-unit (second v))]
    [v (time-unit units)]))

(defn lifespan-params
  [{:keys [ttl idle units] :or {ttl -1 idle -1 units :seconds}}]
  (concat (duration ttl units) (duration idle units)))

(defmacro expire [form]
  `(apply (fn [e# eu# i# iu#]
            (~@(drop-last form) e# eu# i# iu#))
          (lifespan-params ~(last form))))
