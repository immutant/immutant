;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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
  (:require [immutant.cache.config :as config]
            [immutant.registry     :as registry]
            [immutant.util         :as util]
            [clojure.tools.logging :as log])
  (:import [org.infinispan.configuration.cache Configuration ConfigurationBuilder]
           org.infinispan.manager.DefaultCacheManager))

(def service (delay (registry/get org.projectodd.polyglot.cache.as.CacheService/CACHE)))
(def manager (delay (or (and @service (.getCacheContainer @service)) (DefaultCacheManager.))))

(defn clustered? []
  (and @service (.isClustered @service)))

(defn default-mode [opts]
  (if (clustered?)
    (merge {:mode :distributed} opts)
    (do
      (if-not (= :local (:mode opts :local))
        (log/warn "Cache replication only supported when clustered"))
      (assoc opts :mode :local))))

(defn builder
  "Returns an instance of Infinispan's ConfigurationBuilder, a
   mutable factory for creating mostly-immutable Configuration
   instances, e.g. (.build (builder {})).

   Defaults to :distributed with :sync=true for a clustered cache,
   otherwise :local"
  [options]
  (let [opts (merge {:sync true} (default-mode options))]
    (log/info (str "Creating config builder: "
                   (select-keys opts [:mode :sync :locking :persist :max-entries :eviction :tx])))
    (doto (ConfigurationBuilder.)
      (.read (.getDefaultCacheConfiguration @manager))
      (.classLoader (.getContextClassLoader (Thread/currentThread)))
      (config/set-transaction-mode! (nil? @service) opts)
      (config/set-cache-mode! opts)
      (config/set-persistence! opts)
      (config/set-max-entries! opts)
      (config/set-eviction! opts)
      (config/set-locking! opts))))

(defn get-cache
  "Returns the named cache if it exists, otherwise nil"
  ([name]
     (get-cache name @manager))
  ([name manager]
     (if (.isRunning manager name)
       (.getCache manager name))))
  
(defn start
  "Defines and [re]starts a named cache"
  ([name ^Configuration config]
     (start name config @manager))
  ([name ^Configuration config manager]
     (log/debug (str "Infinispan options for cache [" name "]: " config))
     (.defineConfiguration manager name config)
     (when-let [cache (get-cache name manager)]
        (.stop cache)
        (.start cache))
     (let [cache (.getCache manager name)]
       (util/at-exit #(.stop cache))
       cache)))
