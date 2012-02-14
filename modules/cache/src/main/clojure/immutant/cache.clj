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
  (:require [clojure.core.cache]
            [immutant.registry :as lookup])
  (:import [clojure.core.cache CacheProtocol]
           [org.jboss.msc.service ServiceName]
           [org.projectodd.polyglot.core.util ClusterUtil]
           [org.infinispan.config Configuration$CacheMode]
           [org.infinispan.manager DefaultCacheManager]))

(def local-manager (DefaultCacheManager.))

(defn wait-for-clustered-manager []
  (if (and lookup/msc-registry (ClusterUtil/isClustered lookup/msc-registry))
    (loop [attempts 30]
      (let [svc (.append ServiceName/JBOSS (into-array ["infinispan" "web"]))]
        (or (lookup/fetch svc)
            (when (> attempts 0)
              (Thread/sleep 1000)
              (recur (dec attempts))))))))

(def clustered-manager (wait-for-clustered-manager))

(defcache InfinispanCache [cache]
  CacheProtocol
  (lookup [_ key]
    (.get cache key))
  ;; (lookup [_ key not-found]
  ;;   (if (.containsKey cache key)
  ;;     (.get cache key)
  ;;     not-found))
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

(defn cache-mode
  [kw {:keys [sync]}]
  (cond
   (= :distributed kw) (if sync Configuration$CacheMode/DIST_SYNC Configuration$CacheMode/DIST_ASYNC)
   (= :replicated kw) (if sync Configuration$CacheMode/REPL_SYNC Configuration$CacheMode/REPL_ASYNC)
   (= :invalidated kw) (if sync Configuration$CacheMode/INVALIDATION_SYNC Configuration$CacheMode/INVALIDATION_ASYNC)
   (= :local kw) Configuration$CacheMode/LOCAL
   :default (throw (IllegalArgumentException. "Must be one of :distributed, :replicated, :invalidated, or :local"))))

(defn reconfigure
  [name mode]
  (let [cache (.getCache clustered-manager name)
        config (.getConfiguration cache)
        current (.getCacheMode config)]
    (when-not (= mode current)
      (println "Reconfiguring cache" name "from" (str current) "to" (str mode))
      (.stop cache)
      (.setCacheMode config mode)
      (.defineConfiguration clustered-manager name config)
      (.start cache))
    cache))

(defn configure
  [name mode]
  (println "Configuring cache" (str name) "as" (str mode))
  (let [config (.clone (.getDefaultConfiguration clustered-manager))]
    (.setClassLoader config (.getContextClassLoader (Thread/currentThread)))
    (.setCacheMode config mode)
    (.defineConfiguration clustered-manager name config)
    (doto (.getCache clustered-manager name)
      (.start))))

(defn local-cache
  ([] (InfinispanCache. (.getCache local-manager)))
  ([name] (InfinispanCache. (.getCache local-manager name)))
  ([name base] (clojure.core.cache/seed (local-cache name) base)))

(defn clustered-cache
  [name mode & {:as options}]
  (InfinispanCache. (if (.isRunning clustered-manager name)
                      (reconfigure name (cache-mode mode options))
                      (configure name (cache-mode mode options)))))

(defn cache
  "The entry point to determine whether clustered, remote, or local.
   Only local supported currently."
  [& args] (apply local-cache args))

