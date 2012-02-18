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

(ns immutant.cache.core
  (:require [immutant.registry :as registry])
  (:import [org.infinispan.config Configuration$CacheMode]
           [org.infinispan.manager DefaultCacheManager]))

(def local-manager (DefaultCacheManager.))
(def clustered-manager (registry/fetch "jboss.infinispan.web"))

(defn cache-mode
  [kw sync]
  (cond
   (= :invalidated kw) (if sync Configuration$CacheMode/INVALIDATION_SYNC Configuration$CacheMode/INVALIDATION_ASYNC)
   (= :distributed kw) (if sync Configuration$CacheMode/DIST_SYNC Configuration$CacheMode/DIST_ASYNC)
   (= :replicated kw) (if sync Configuration$CacheMode/REPL_SYNC Configuration$CacheMode/REPL_ASYNC)
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

(defn clustered-cache
  [name & {:keys [mode sync] :or {mode :invalidated}}]
  (if (.isRunning clustered-manager name)
    (reconfigure name (cache-mode mode sync))
    (configure name (cache-mode mode sync))))

(defn local-cache
  ([] (.getCache local-manager))
  ([name] (.getCache local-manager name)))

(defn best-cache
  "Returns the raw Infinispan cache, either clustered or local"
  ([name] (best-cache name nil))
  ([name mode]
     (if clustered-manager
       (clustered-cache name :mode (or mode :invalidated))
       (do
         (if (and mode (not= mode :local))
           (println "WARN: Invalid mode," mode ", falling back to local"))
         (local-cache name)))))

(defn lifespan-params [{:keys [ttl idle units] :or {ttl -1 idle -1 units :seconds}}]
  (let [u (.toUpperCase (name units))
        tu (java.util.concurrent.TimeUnit/valueOf u)]
    (list ttl tu idle tu)))

(defmacro expire [form]
  `(apply (fn [e# eu# i# iu#]
            (~@(drop-last form) e# eu# i# iu#))
          (lifespan-params ~(last form))))

