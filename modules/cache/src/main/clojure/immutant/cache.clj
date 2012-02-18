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
  (:use [immutant.cache.core]
        [immutant.codecs]
        [clojure.core.cache :only (defcache)])
  (:require [clojure.core.cache])
  (:import [clojure.core.cache CacheProtocol]
           [java.util.concurrent TimeUnit]))

(defprotocol Mutable
  (put [cache key value] [cache key value options])
  (put-all [cache map] [cache map options])
  (put-if-absent [cache key value] [cache key value options])
  (put-if-present [cache key value] [cache key value options])
  (put-if-replace [cache key old new] [cache key old new options])
  (delete [cache key] [cache key value])
  (clear [cache]))

(defcache InfinispanCache [cache]

  CacheProtocol
  (lookup [_ key]
    (decode (.get cache (encode key))))
  ;; Added in version 0.6.0, as yet unreleased
  ;; (lookup [_ key not-found]
  ;;   (if (.containsKey cache key)
  ;;     (.get cache key)
  ;;     not-found))
  (has? [_ key]
    (.containsKey cache (encode key)))
  (hit [this key] this)
  (miss [this key value]
    (put this key value)
    this)
  (evict [this key]
    (and key (delete this key))
    this)
  (seed [this base]
    (and base (put-all this base))
    this)

  Mutable
  (put [this k v] (put this k v {}))
  (put [_ k v opts]
    (decode (expire (.put cache (encode k) (encode v) opts))))
  (put-all [this m] (put-all this m {}))
  (put-all [_ m opts]
    (expire (.putAll cache (into {} (for [[k v] m] [(encode k) (encode v)])) opts)))
  (put-if-absent [this k v] (put-if-absent this k v {}))
  (put-if-absent [_ k v opts]
    (decode (expire (.putIfAbsent cache (encode k) (encode v) opts))))
  (put-if-present [this k v] (put-if-present this k v {}))
  (put-if-present [_ k v opts]
    (decode (expire (.replace cache (encode k) (encode v) opts))))
  (put-if-replace [this k old v] (put-if-replace this k old v {}))
  (put-if-replace [_ k old v opts]
    (expire (.replace cache (encode k) (encode old) (encode v) opts)))
  (delete [_ key] (decode (.remove cache (encode key))))
  (delete [_ key value] (.remove cache (encode key) (encode value)))
  (clear [_] (.clear cache))

  Object
  (toString [_] (str cache)))

(defn cache
  "The entry point to determine whether clustered or local"
  ([name] (cache name nil nil))
  ([name v] (if (keyword? v) (cache name v nil) (cache name nil v)))
  ([name mode base]
     (clojure.core.cache/seed (InfinispanCache. (raw-cache name mode)) base)))
     