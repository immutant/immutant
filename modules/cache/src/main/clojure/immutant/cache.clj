;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
        [immutant.codecs])
  (:require [clojure.core.cache :as cc]
            [clojure.core.memoize :as cm])
  (:import [clojure.core.memoize PluggableMemoization]))

(defprotocol Mutable
  "Functions for manipulating a shared, distributed cache.

   Every function optionally accepts a map with the following
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
    "Delete the entry; value must match current if passed"))

(deftype InfinispanCache [cache]

  cc/CacheProtocol
  (lookup [this key]
    (.valAt this key))
  (has? [this key]
    (.containsKey this key))
  (hit [this key] this)
  (miss [this key value]
    (assoc this key value))
  (evict [this key]
    (.without this key))
  (seed [this base]
    (put-all this base)
    this)

  Mutable
  (put [this k v] (put this k v {}))
  (put [_ k v opts]
    (decode (expire (.put cache (encode k) (encode v) opts))))
  (put-all [this m] (put-all this m {}))
  (put-all [_ m opts]
    (and m (expire (.putAll cache (into {} (for [[k v] m] [(encode k) (encode v)])) opts))))
  (put-if-absent [this k v] (put-if-absent this k v {}))
  (put-if-absent [_ k v opts]
    (decode (expire (.putIfAbsent cache (encode k) (encode v) opts))))
  (put-if-present [this k v] (put-if-present this k v {}))
  (put-if-present [_ k v opts]
    (decode (expire (.replace cache (encode k) (encode v) opts))))
  (put-if-replace [this k old v] (put-if-replace this k old v {}))
  (put-if-replace [_ k old v opts]
    (expire (.replace cache (encode k) (encode old) (encode v) opts)))
  (delete [_ key] (and key (decode (.remove cache (encode key)))))
  (delete [_ key value] (.remove cache (encode key) (encode value)))

  clojure.lang.Seqable
  (seq [_]
    (and (seq cache)
         (for [[k v] (seq cache)]
           (clojure.lang.MapEntry. (decode k) (decode v)))))

  clojure.lang.ILookup
  (valAt [this key]
    (decode (.get cache (encode key))))
  (valAt [this key not-found]
    (if (.containsKey this key)
      (.valAt this key)
      not-found))
  
  clojure.lang.IPersistentMap
  (assoc [this k v]
    (.cons this [k v]))
  (without [this k]
    (and k (.remove cache (encode k)))
    this)

  clojure.lang.Associative
  (containsKey [this k]
    (.containsKey cache (encode k)))
  (entryAt [this k]
    (when (.containsKey this k)
      (clojure.lang.MapEntry. k (.valAt this k))))

  clojure.lang.Counted
  (count [_]
    (clojure.core/count cache))
  
  clojure.lang.IPersistentCollection
  (cons [this elem]
    (if (map? elem)
      (.put-all this elem)
      (.put this (first elem) (second elem)))
    this)
  (empty [this]
    (.clear cache)
    this)
  (equiv [_ other]
    (.equals cache other))

  java.lang.Iterable
  (iterator [this] (.iterator cache))

  Object
  (toString [_] (str cache)))

;; Workaround the non-serializable Delay objects cached by
;; core.memoize and force every key to be a vector so that decoded
;; comparisons work correctly
(deftype DelayedCache [cache delayed]
  cc/CacheProtocol
  ;; We assume value is a delay, which we can't serialize and don't
  ;; want to force yet
  (miss [this key value]
    (swap! delayed assoc (vec key) (delay (cc/miss cache (vec key) @value) @value))
    this)
  (lookup [_ key]
    (when-let [value (get @delayed (vec key))]
      (deref value)
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
  "Returns an implementation of Mutable and
   core.cache/CacheProtocol. A cache name is the only required
   argument. When two arguments are passed, the second may be either a
   symbol indicating replication mode or a seed, i.e. either the
   second or the third of the 3-argument signature.

   Replication mode should be one of:
     :local, :invalidated, :distributed, or :replicated

   Although the entries in the passed base hash map will be added to
   the cache, entries already in the cache will NOT be deleted"
  ([name] (cache name nil nil))
  ([name v] (if (keyword? v) (cache name v nil) (cache name nil v)))
  ([name mode base]
     (cc/seed (InfinispanCache. (raw-cache name mode)) base)))

(defn memo
  "Memoize a function by associating its arguments with return values
   stored in a possibly-clustered Infinispan-backed cache. Other than
   the function to be memoized, arguments are the same as for the
   cache function."
  ([f name] (memo f name nil nil))
  ([f name v] (if (keyword? v) (memo f name v nil) (memo f name nil v)))
  ([f name mode seed]
     (cm/build-memoizer
      #(PluggableMemoization. %1 (DelayedCache. (cache %2 %3 %4) (atom {})))
      f
      name
      mode
      seed)))

