;; Copyright 2014 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.caching
  "Create, manage and manipulate Infinispan caches in a data grid"
  (:refer-clojure :exclude (swap!))
  (:require [immutant.internal.options :refer :all]
            [immutant.internal.util    :refer [kwargs-or-map->map]]
            [immutant.codecs           :refer [lookup-codec]]
            [immutant.caching.options  :refer [wash]]
            [clojure.walk              :refer [keywordize-keys]])
  (:import [org.projectodd.wunderboss WunderBoss Options]
           [org.projectodd.wunderboss.caching Caching Caching$CreateOption Config]))

(defn- component [] (WunderBoss/findOrCreateComponent Caching))

(defn cache
  "Returns an
   [org.infinispan.Cache](https://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/Cache.html).
   A name is the only required argument. If a cache by that name
   already exists, it will be returned, and any options passed to this
   function will be ignored. To force reconfiguration, call {{stop}}
   before calling {{cache}}.

   The following groups of options are supported, each listed with its
   [default] value. A negative value for any numeric option means
   \"unlimited\".

   Durability: entries can persist to disk via the `:persist` option.
   If set to `true`, cache entries will persist in the current
   directory. Override this by setting `:persist` to a string naming
   the desired directory.

     `:persist` If non-nil, data persists across server restarts in
                a file store; a string value names the directory [nil]

   Eviction: turned off by default, `:max-entries` may be set to
   mitigate the risk of memory exhaustion. When :persist is enabled,
   evicted entries are written to disk, so that the entries in the
   file store are a superset of those in RAM, transparently reloaded
   upon request.

     `:max-entries` The maximum number of entries allowed in the cache [-1]
     `:eviction`    How entries are evicted when `:max-entries` is exceeded [:none]
                    one of `:none`, `:lru`, `:lirs`, or `:unordered` 

   Expiration: both time-to-live and max idle limits are supported.
   Values may be a number of milliseconds, a period keyword, or
   multiplier/keyword pairs, e.g. `(every 1 :hour 20 :minutes)`. Both
   singular and plural versions of :second, :minute, :hour, :day, and
   :week are valid period keywords.

     `:ttl`  The max time the entry will live before expiry [-1]
     `:idle` The time after which an entry will expire if not accessed [-1]

   Replication: the replication mode defaults to `:dist-sync` when
   clustered. When not clustered, the value of `:mode` is ignored, and
   the cache will be `:local`. Asynchronous replication may yield a
   slight performance increase at the risk of potential cache
   inconsistency.

     `:mode` Replication mode [:dist-sync or :local]
             one of :local, :repl-sync, :repl-async,
             :invalidation-sync, :invalidation-async,
             :dist-sync, :dist-async

   Transactions: caches can participate in transactions when a
   TransactionManager is available.

     `:transactional` Whether the cache is transactional [false]
     `:locking`       Transactional locking schemes [:optimistic]
                      one of :optimisitic or :pessimistic

   Advanced configuration: the options listed above are the most
   commonly configured, but Infinispan has many more buttons,
   switches, dials, knobs and levers. Call the {{builder}} function to
   create your own Configuration instance and pass it in via the
   `:configuration` option.

     `:configuration` an org.infinispan.configuration.cache.Configuration instance"
  ([name]
    (cache name {}))
  ([name k v & kvs]
    (cache name (apply hash-map k v kvs)))
  (^org.infinispan.Cache [name opts]
    (let [options (-> opts
                    keywordize-keys
                    (validate-options cache)
                    wash
                    (extract-options Caching$CreateOption))]
      (.findOrCreate (component) name options))))

(set-valid-options! cache (opts->set Caching$CreateOption))

(defn with-codec
  "Returns a cache that stores/retrieves entries in the passed cache
  using the passed codec. The default is `:none` but the following are
  supported: `:edn`, `:json`, and `:fressian`. To use the latter two,
  the `cheshire` and `org.clojure/data.fressian` libraries,
  respectively, must be available on the classpath."
  [cache codec]
  (.encodedWith (component)
    (lookup-codec codec)
    cache))

(defn swap!
  "Atomically swaps the value associated to the key in the cache with
  the result of applying f, passing the current value as the first
  param along with any args, returning the new cached value. Function
  f should have no side effects, as it may be called multiple times.
  If the key is missing, the result of applying f to nil will be
  inserted atomically."
  [^org.infinispan.Cache cache key f & args]
  (loop [val (get cache key)]
    (let [new (apply f val args)]
      (if (or val (contains? cache key))
        (if (.replace cache key val new)
          new
          (recur (get cache key)))
        (if (nil? (.putIfAbsent cache key new))
          new
          (recur (get cache key)))))))

(defn exists?
  "Return true if the named cache exists"
  [name]
  (boolean (.find (component) name)))

(defn stop
  "Removes a cache, allowing it to be reconfigured by the cache
  function."
  [cache-or-name]
  (if (instance? org.infinispan.Cache cache-or-name)
    (.stop (component) (.getName cache-or-name))
    (.stop (component) (name cache-or-name))))

(defn builder
  "For advanced use, call this function to obtain a fluent Infinispan
  configuration builder. Set the desired options, and invoke its build
  method, the result from which can be passed in the :configuration
  option of the cache function."
  [& options]
  (Config/builder (-> options
                    kwargs-or-map->map
                    keywordize-keys
                    (validate-options cache builder)
                    wash
                    (extract-options Caching$CreateOption)
                    (Options.))))
