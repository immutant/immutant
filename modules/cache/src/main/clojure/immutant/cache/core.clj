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
  (:use [immutant.try :only [try-def try-defn]])
  (:require [immutant.registry :as registry]
            [clojure.tools.logging :as log]))

(def reqs '(import '[org.infinispan.configuration.cache ConfigurationBuilder VersioningScheme CacheMode]
                   '[org.infinispan.transaction TransactionMode LockingMode]
                   'org.infinispan.manager.DefaultCacheManager
                   'org.infinispan.transaction.lookup.GenericTransactionManagerLookup
                   'org.infinispan.util.concurrent.IsolationLevel))

(def clustered-manager (registry/fetch "jboss.infinispan.web"))
(try-def reqs local-manager (delay (DefaultCacheManager.)))

(try-defn reqs cache-mode
          [{:keys [mode sync]}]
          (cond
           (= :invalidated mode) (if sync CacheMode/INVALIDATION_SYNC CacheMode/INVALIDATION_ASYNC)
           (= :distributed mode) (if sync CacheMode/DIST_SYNC CacheMode/DIST_ASYNC)
           (= :replicated mode) (if sync CacheMode/REPL_SYNC CacheMode/REPL_ASYNC)
           :else CacheMode/LOCAL))

(try-defn reqs set-optimistic-locking!
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
          
(try-defn reqs set-pessimistic-locking!
          [builder]
          (.. builder transaction
              (lockingMode LockingMode/PESSIMISTIC))
          builder)
          
(try-defn reqs build-config
          [{:keys [locking template] :as opts}]
          (let [builder (ConfigurationBuilder.)]
            (if template (.read builder template))
            (.classLoader builder (.getContextClassLoader (Thread/currentThread)))
            (.. builder transaction
                (transactionManagerLookup (GenericTransactionManagerLookup.))
                (transactionMode TransactionMode/TRANSACTIONAL))
            (.. builder clustering
                (cacheMode (cache-mode opts)))
            (cond
             (= locking :pessimistic) (set-pessimistic-locking! builder)
             (= locking :optimistic) (set-optimistic-locking! builder)
             locking (throw (IllegalArgumentException. (str "Invalid locking mode: " locking))))
            (.build builder)))

(try-defn reqs reconfigure
          [manager name opts]
          (let [cache (.getCache manager name)]
            (when-not (= (cache-mode opts) (.. cache getCacheConfiguration clustering cacheMode))
              (println "JC: why am i reconfiguring?")
              (log/info "Reconfiguring cache" name)
              (.stop cache)
              (.defineConfiguration manager name (build-config opts))
              (.start cache))
            cache))

(try-defn reqs configure
          [manager name opts]
          (log/info "Configuring cache" name)
          (.defineConfiguration manager name
                                (build-config (merge {:template (.getDefaultCacheConfiguration manager)} opts)))
          (doto (.getCache manager name)
            (.start)))

(defn configure-cache
  "Defaults to :distributed, with :sync=true, for a clustered cache"
  [manager name opts]
  (let [opts (merge {:mode :distributed, :sync true} opts)]
    (if (.isRunning manager name)
      (reconfigure manager name opts)
      (configure manager name opts))))

(defn raw-cache
  "Returns the raw Infinispan cache, clustered if possible, otherwise local"
  [name {:keys [mode] :or {mode :local} :as opts}]
  (cond
   clustered-manager (configure-cache clustered-manager name opts)
   local-manager (do
                   (if (not= mode :local)
                     (log/warn "Invalid mode," mode ", falling back to local"))
                   (configure-cache @local-manager name (assoc opts :mode :local)))
   :else (log/error "Infinispan not found on the classpath")))

(defn lifespan-params [{:keys [ttl idle units] :or {ttl -1 idle -1 units :seconds}}]
  (let [u (.toUpperCase (name units))
        tu (java.util.concurrent.TimeUnit/valueOf u)]
    (list ttl tu idle tu)))

(defmacro expire [form]
  `(apply (fn [e# eu# i# iu#]
            (~@(drop-last form) e# eu# i# iu#))
          (lifespan-params ~(last form))))

