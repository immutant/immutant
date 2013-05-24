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

   The :units option applies to both :idle and :ttl, but to achieve
   finer granularity you may alternatively pass a two element vector
   containing the amount and units, e.g. {:ttl [5 :hours]}.

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
    (.containsKey cache (encode key (:encoding options))))
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
    (let [opts (merge options opts)
          enc (:encoding opts)]
      (decode (expire (.put cache (encode k enc) (encode v enc) opts)) enc)))
  (put-all [this m] (put-all this m {}))
  (put-all [_ m opts]
    (let [opts (merge options opts)
          enc (:encoding opts)]
      (and m (expire (.putAll cache (into {} (for [[k v] m] [(encode k enc) (encode v enc)])) opts)))))
  (put-if-absent [this k v] (put-if-absent this k v {}))
  (put-if-absent [_ k v opts]
    (let [opts (merge options opts)
          enc (:encoding opts)]
      (decode (expire (.putIfAbsent cache (encode k enc) (encode v enc) opts)) enc)))
  (put-if-present [this k v] (put-if-present this k v {}))
  (put-if-present [_ k v opts]
    (let [opts (merge options opts)
          enc (:encoding opts)]
      (decode (expire (.replace cache (encode k enc) (encode v enc) opts)) enc)))
  (put-if-replace [this k old v] (put-if-replace this k old v {}))
  (put-if-replace [_ k old v opts]
    (let [opts (merge options opts)
          enc (:encoding opts)]
      (expire (.replace cache (encode k enc) (encode old enc) (encode v enc) opts))))
  (delete [_ key]
    (and key (let [enc (:encoding options)] (decode (.remove cache (encode key enc)) enc))))
  (delete [_ key value] (let [e (:encoding options)] (.remove cache (encode key e) (encode value e))))
  (delete-all [this] (.clear cache) this)

  clojure.lang.Seqable
  (seq [_]
    (and (seq cache)
         (let [enc (:encoding options)]
           (for [[k v] (seq cache)]
             (clojure.lang.MapEntry. (decode k enc) (decode v enc))))))

  java.util.Map
  (containsKey [_ key]
    (.containsKey cache (encode key (:encoding options))))
  (get [_ key]
    (let [enc (:encoding options)]
      (decode (.get cache (encode key enc)) enc)))
  
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

(defn create
  "Returns an object that implements both Mutable and
   core.cache/CacheProtocol. A name is the only required argument. If
   a cache by that name already exists, it will be restarted and all
   its entries lost. Use lookup to obtain a reference to an existing
   cache. The following options are supported:

   The following options are supported [default]:
     :mode        Replication mode [:distributed or :local]
                    :local, :invalidated, :distributed, or :replicated
     :sync        Whether replication occurs synchronously [true]
     :persist     If non-nil, data persists across server restarts in a file
                    store; a string value names the directory [nil]
     :seed        A hash of initial entries [nil]
     :locking     Infinispan locking schemes [nil]
                    :optimisitic or :pessimistic
     :encoding    :edn :json or :none [:edn]
     :max-entries The maximum number of entries allowed in the cache [-1]
     :eviction    How entries are evicted when :max-entries is exceeded [:lirs]
                    :lru, :lirs, or :unordered
     :ttl         The max time the entry will live before expiry [-1]
     :idle        The time after which an entry will expire if not accessed [-1]
     :units       The units for the values of :ttl and :idle [:seconds]

   The replication mode defaults to :distributed when clustered. When
   not clustered, the value of :mode is ignored, and the cache will
   be :local.

   If :persist is true, cache entries will persist in the current
   directory. Override this by setting :persist to a string naming the
   desired directory.

   A negative value for any numeric option means \"unlimited\".

   The lifespan-oriented options (:ttl :idle :units) become the
   default options for the functions of the Mutable protocol. But any
   options passed to those functions take precedence over these. See
   the Mutable doc for more info."
  [name & {:keys [seed] :as options}]
  (cc/seed (InfinispanCache. (configure-cache name options) options) seed))

(def ^{:doc "Deprecated; use create instead" :no-doc true} cache #'create)

(defn lookup
  "Looks up a cache by name and returns it; returns nil if the cache doesn't exist.

   All but the :encoding and lifespan-oriented create
   options (:ttl :idle :units) are ignored if passed here."
  [name & {:as options}]
  (if-let [c (get-cache name)]
    (InfinispanCache. c options)))

(defn lookup-or-create
  "A convenience method for creating a cache only if it doesn't
   already exist. Takes the same options as create"
  [name & opts]
  (or (apply lookup name opts) (apply create name opts)))
  
(defn memo
  "Memoize a function by associating its arguments with return values
   stored in a possibly-clustered Infinispan-backed cache. Other than
   the function to be memoized, arguments are the same as for the
   create function."
  [f name & options]
  (cm/build-memoizer
   #(PluggableMemoization. %1 (DelayedCache. (apply lookup-or-create %2 %3) (atom {})))
   f
   name
   options))
