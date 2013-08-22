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

(ns immutant.cache.config
  (:import [org.infinispan.configuration.cache VersioningScheme CacheMode]
           [org.infinispan.transaction TransactionMode LockingMode]
           org.infinispan.transaction.lookup.GenericTransactionManagerLookup
           org.infinispan.eviction.EvictionStrategy
           org.infinispan.util.concurrent.IsolationLevel
           java.util.concurrent.TimeUnit))

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

(defn set-transaction-mode!
  [builder use-synchronization]
  (.. builder
      transaction
      (transactionManagerLookup (GenericTransactionManagerLookup.))
      (transactionMode TransactionMode/TRANSACTIONAL)
      (useSynchronization (not (not use-synchronization)))))

(defn set-optimistic-locking!
  [builder]
  (.. builder
      transaction
      (lockingMode LockingMode/OPTIMISTIC)
      (useSynchronization false)
      versioning
      (enabled true)
      (scheme VersioningScheme/SIMPLE)
      locking
      (isolationLevel IsolationLevel/REPEATABLE_READ)
      (writeSkewCheck true)))

(defn set-pessimistic-locking!
  [builder]
  (.. builder
      transaction
      (lockingMode LockingMode/PESSIMISTIC)
      (useSynchronization false)))

(defn set-cache-mode!
  [builder opts]
  (.. builder clustering (cacheMode (cache-mode opts))))

(defn set-persistence!
  [builder {persist :persist}]
  (if persist
    (let [store (.. builder loaders addFileCacheStore)]
      (if (string? persist)
        (.. store (location persist)))))
  builder)

(defn set-max-entries!
  [builder {max-entries :max-entries}]
  (if max-entries
    (.. builder eviction (maxEntries max-entries))
    builder))

(defn set-eviction!
  [builder {eviction :eviction}]
  (if eviction
    (.. builder eviction (strategy (eviction-strategy eviction)))
    builder))

(defn set-locking!
  [builder {locking :locking}]
  (cond
   (= locking :pessimistic) (set-pessimistic-locking! builder)
   (= locking :optimistic) (set-optimistic-locking! builder)
   locking (throw (IllegalArgumentException. (str "Invalid locking mode: " locking)))
   :else builder))

(defn- time-unit
  [kw]
  (let [n (name kw)
        n (if (.endsWith n "s") n (str n "s"))]
    (TimeUnit/valueOf (.toUpperCase n))))

(defn- duration
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
