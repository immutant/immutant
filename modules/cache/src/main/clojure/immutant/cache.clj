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
  (:use [clojure.core.cache :only (defcache)])
  (:require [clojure.core.cache])
  (:import [clojure.core.cache CacheProtocol]
           [org.infinispan.manager DefaultCacheManager]))

(def local-manager (DefaultCacheManager.))

(defcache InfinispanCache [cache]
  CacheProtocol
  (lookup [_ key]
    (.get cache key))
  (lookup [_ key not-found]
    (if (.containsKey cache key)
      (.get cache key)
      not-found))
  (has? [_ key]
    (.containsKey cache key))
  (hit [this key] this)
  (miss [this key value]
    (.put cache key value)
    this)
  (evict [this key]
    (and key (.remove cache key))
    this)
  (seed [this base]
    (and base (.putAll cache base))
    this)

  Object
  (toString [_] (str cache)))

(defn local-cache
  ([] (InfinispanCache. (.getCache local-manager)))
  ([name] (InfinispanCache. (.getCache local-manager name)))
  ([name base] (clojure.core.cache/seed (local-cache name) base)))

(defn cache
  "The entry point to determine whether clustered, remote, or local.
   Only local supported currently."
  [& args] (apply local-cache args))