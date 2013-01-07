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

(ns immutant.cache
  "Infinispan-backed implementations of core.cache and core.memoize
   protocols supporting multiple replication options and more."
  (:use [immutant.cache.core]
        [immutant.codecs :only [encode decode]])
  (:require [clojure.core.cache :as cc]
            [clojure.core.memoize :as cm])
  (:import [clojure.core.memoize PluggableMemoization]))

(defprotocol Mutable
  "Functions for manipulating a shared, distributed cache.

   Every 'put' function optionally accepts a map with the following
   lifespan-oriented keys:

     :ttl - time-to-live, the max time the entry will live before expiry [-1]
     :idle - the time after which an entry will expire if not accessed [-1]
     :units - the units for the values of :ttl and :idle [:seconds]

   Negative values imply no expiration.
   Possible values for :units -- :days, :hours, :minutes, :seconds,
                                 :milliseconds, :microseconds :nanoseconds

   The conditional functions, e.g. put-if-*, are all atomic."
  (put [cache key value] [cache key value options]
    "Put an entry in the cache")
  (put-all [cache map] [cache map options]
    "Put all the entries in cache")
  (put-if-absent [cache key value] [cache key value options]
    "Put it in only if key is not already there")
  (put-if-present [cache key value] [cache key value options]
    "Put it in only if key is already there")
  (put-if-replace [cache key old new] [cache key old new options]
    "Put it in only if key is there and current matches old")
  (delete [cache key] [cache key value]
    "Delete the entry; value must match current if passed")
  (delete-all [cache]
    "Clear all entries from the cache and return it"))

(deftype InfinispanCache [cache options]

  cc/CacheProtocol
  (lookup [this key]
    (.valAt this key))
  (has? [this key]
    (.containsKey cache (encode key)))
  (hit [this key] this)
  (miss [this key value]
    (put this key value)
    this)
  (evict [this key]
    (delete this key)
    this)
  (seed [this base]
    (if base (put-all (delete-all this) base))
    this)

  Mutable
  (put [this k v] (put this k v {}))
  (put [_ k v opts]
    (decode (expire (.put cache (encode k) (encode v) (merge options opts)))))
  (put-all [this m] (put-all this m {}))
  (put-all [_ m opts]
    (and m (expire (.putAll cache (into {} (for [[k v] m] [(encode k) (encode v)])) (merge options opts)))))
  (put-if-absent [this k v] (put-if-absent this k v {}))
  (put-if-absent [_ k v opts]
    (decode (expire (.putIfAbsent cache (encode k) (encode v) (merge options opts)))))
  (put-if-present [this k v] (put-if-present this k v {}))
  (put-if-present [_ k v opts]
    (decode (expire (.replace cache (encode k) (encode v) (merge options opts)))))
  (put-if-replace [this k old v] (put-if-replace this k old v {}))
  (put-if-replace [_ k old v opts]
    (expire (.replace cache (encode k) (encode old) (encode v) (merge options opts))))
  (delete [_ key] (and key (decode (.remove cache (encode key)))))
  (delete [_ key value] (.remove cache (encode key) (encode value)))
  (delete-all [this] (.clear cache) this)

  clojure.lang.Seqable
  (seq [_]
    (and (seq cache)
         (for [[k v] (seq cache)]
           (clojure.lang.MapEntry. (decode k) (decode v)))))

  java.util.Map
  (containsKey [_ key]
    (.containsKey cache (encode key)))
  (get [_ key]
    (decode (.get cache (encode key))))
  
  clojure.lang.ILookup
  (valAt [this key]
    (.get this key))
  (valAt [this key not-found]
    (if (.containsKey this key)
      (.get this key)
      not-found))

  clojure.lang.Counted
  (count [_]
    (clojure.core/count cache))
  
  Object
  (toString [this] (str (into {} (seq this)))))

;; Workaround the non-serializable Delay objects cached by
;; core.memoize and force every key to be a vector so that decoded
;; comparisons work correctly
(deftype DelayedCache [cache delayed]
  cc/CacheProtocol
  ;; We assume value is a delay, which we can't serialize and don't
  ;; want to force yet
  (miss [this key value]
    (swap! delayed
           (fn [m k v] (if (contains? m k) m (assoc m k v)))
           (vec key)
           (delay (cc/miss cache (vec key) @value) @value))
    this)
  (lookup [_ key]
    (when-let [value (get @delayed (vec key))]
      (force value)
      (swap! delayed dissoc (vec key)))
    ;; Callers expect to deref the returned value
    (reify
      clojure.lang.IDeref
      (deref [this] (cc/lookup cache (vec key)))))
  (seed [this base] (doseq [[k v] base] (cc/miss this k v)) this)
  (has? [_ key] (or (contains? @delayed (vec key)) (cc/has? cache (vec key))))
  (hit [this key] (cc/hit cache (vec key)) this)
  (evict [this key] (cc/evict cache (vec key)) this)

  clojure.lang.Seqable
  (seq [this]
    (and (seq cache)
         (for [[k v] (seq cache)]
           (clojure.lang.MapEntry. k (cc/lookup this k))))))

(defn cache
  "Returns an object that implements both Mutable and
   core.cache/CacheProtocol. A name is the only required argument. The
   following options are supported:

   The following options are supported [default]:
     :mode     Replication mode [:invalidated or :local]
                 :local, :invalidated, :distributed, or :replicated
     :persist  If non-nil, data persists across server restarts in a file
                 store; a string value names the directory [nil]
     :seed     A hash of initial entries [nil]
     :locking  Infinispan locking schemes [nil]
                 :optimisitic or :pessimistic
     :ttl      The max time the entry will live before expiry [-1]
     :idle     The time after which an entry will expire if not accessed [-1]
     :units    The units for the values of :ttl and :idle [:seconds]

   The replication mode defaults to :invalidated when clustered. When
   not clustered, the value of :mode is ignored, and the cache will
   be :local.

   If :persist is true, cache entries will persist in the directory
   named by immutant.cache.core/file-store-path. Override this by
   setting :persist to a string naming the desired directory.

   Seeding the cache will delete any existing entries.

   The lifespan-oriented options (:ttl :idle :units) become the
   default options for the functions of the Mutable protocol. But any
   options passed to those functions take precedence over these."
  [name & {:keys [mode seed] :as options}]
  (cc/seed (InfinispanCache. (raw-cache name options) options) seed))

(defn memo
  "Memoize a function by associating its arguments with return values
   stored in a possibly-clustered Infinispan-backed cache. Other than
   the function to be memoized, arguments are the same as for the
   cache function."
  [f name & options]
  (cm/build-memoizer
   #(PluggableMemoization. %1 (DelayedCache. (apply cache %2 %3) (atom {})))
   f
   name
   options))
