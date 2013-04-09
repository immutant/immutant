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
  [{:keys [locking template persist] :as opts}]
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
    (cond
      (= locking :pessimistic) (set-pessimistic-locking! builder)
      (= locking :optimistic) (set-optimistic-locking! builder)
      locking (throw (IllegalArgumentException. (str "Invalid locking mode: " locking))))
    (.build builder)))

(defn same-config?
  [c1 c2]
  (and (= (.. c1 clustering cacheMode) (.. c2 clustering cacheMode))
       (= (.. c1 transaction transactionMode) (.. c2 transaction transactionMode))
       (= (.. c1 transaction lockingMode) (.. c2 transaction lockingMode))
       (or (= (.. c1 loaders) (.. c2 loaders))
           (and (= (.. c1 loaders cacheLoaders size)
                   (.. c2 loaders cacheLoaders size))
                (= (.. c1 loaders cacheLoaders (get 0) location)
                   (.. c2 loaders cacheLoaders (get 0) location))))))

(defn reconfigure
  [manager name opts]
  (let [cache (.getCache manager name)
        current (.getCacheConfiguration cache)
        desired (build-config opts)]
    (when-not (same-config? current desired)
      (log/info "Reconfiguring cache" name)
      (.stop cache)
      (.defineConfiguration manager name desired)
      (.start cache))
    cache))

(defn configure
  [manager name opts]
  (log/info "Configuring cache" name)
  (let [config (.getDefaultCacheConfiguration manager)]
    (.defineConfiguration manager name (build-config (merge {:template config} opts))))
  (.getCache manager name))

(defn default-mode []
  (if (and service (.isClustered service))
    :distributed
    :local))

(defn configure-cache
  "Defaults to :distributed, with :sync=true, for a clustered cache"
  [manager name opts]
  (let [opts (merge {:mode (default-mode), :sync true} opts)]
    (if (.isRunning manager name)
      (reconfigure manager name opts)
      (configure manager name opts))))

(defn raw-cache
  "Returns the raw Infinispan cache, clustered if possible, otherwise local"
  [name {:as opts}]
  (configure-cache @manager name opts))

(defn lifespan-params [{:keys [ttl idle units] :or {ttl -1 idle -1 units :seconds}}]
  (let [u (.toUpperCase (name units))
        tu (java.util.concurrent.TimeUnit/valueOf u)]
    (list ttl tu idle tu)))

(defmacro expire [form]
  `(apply (fn [e# eu# i# iu#]
            (~@(drop-last form) e# eu# i# iu#))
          (lifespan-params ~(last form))))
