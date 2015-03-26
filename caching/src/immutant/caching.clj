;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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
  "Create, manage and manipulate [Infinispan](http://infinispan.org)
  caches in a data grid"
  (:require [immutant.internal.options :refer :all]
            [immutant.internal.util    :refer [kwargs-or-map->map]]
            [immutant.codecs           :refer [lookup-codec]]
            [immutant.caching.options  :refer [wash listener]])
  (:import [org.projectodd.wunderboss WunderBoss Options]
           [org.projectodd.wunderboss.caching Caching Caching$CreateOption Config]))

(defn- ^Caching component [] (WunderBoss/findOrCreateComponent Caching))

(defn cache
  "Returns a reference to the cache named by `name`, creating it if necessary.

   `name` is the only required argument. If a cache by that name
   already exists, it will be returned, and any options passed to this
   function will be ignored. To force reconfiguration, call [[stop]]
   before calling [[cache]]. The return value is a
   [org.infinispan.Cache](https://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/Cache.html),
   an extension of `java.util.concurrent.ConcurrentMap`.

   The following groups of options are supported, each listed with its
   [default] value. A negative value for any numeric option means
   \"unlimited\".

   Durability: entries can persist to disk via the :persist option. If
   set to `true`, cache entries will persist in the current directory.
   Override this by setting :persist to a string naming the desired
   directory.

   * :persist - if non-nil, data persists across server restarts in a
                file store; a string value names the directory [nil]

   Eviction: turned off by default, :max-entries may be set to
   mitigate the risk of memory exhaustion. When :persist is enabled,
   evicted entries are written to disk, so that the entries in memory
   are a subset of those in the file store, transparently reloaded
   upon request. The eviction policy may be one of :none, :lru, :lirs,
   or :unordered

   * :max-entries - the max number of entries allowed in the cache [-1]
   * :eviction - how entries are evicted when :max-entries is exceeded [:none]

   Expiration: both time-to-live and max idle limits are supported.
   Values may be a number of milliseconds, a period keyword, or
   multiplier/keyword pairs, e.g. `[1 :hour 20 :minutes]`. Both
   singular and plural versions of :second, :minute, :hour, :day, and
   :week are valid period keywords.

   * :ttl - the max time the entry will live before expiry [-1]
   * :idle - the time after which an entry will expire if not accessed [-1]

   Replication: the replication mode defaults to :dist-sync when
   clustered. When not clustered, the value of :mode is ignored, and
   the cache will be :local. Asynchronous replication may yield a
   slight performance increase at the risk of potential cache
   inconsistency.

   * :mode - replication mode, one of :local,
             :repl-sync, :repl-async, :invalidation-sync,
             :invalidation-async, :dist-sync, :dist-async [:dist-sync or :local]

   Transactions: caches can participate in transactions when a
   TransactionManager is available. The locking scheme may be either
   :optimisitic or :pessimistic

   * :transactional? - whether the cache is transactional [false]
   * :locking - transactional locking scheme [:optimistic]

   Advanced configuration: the options listed above are the most
   commonly configured, but Infinispan has many more buttons,
   switches, dials, knobs and levers. Call the [[builder]] function to
   create your own Configuration instance and pass it in via the
   :configuration option.

   * :configuration - a [Configuration](https://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/configuration/cache/Configuration.html) instance"
  ^org.infinispan.Cache [name & opts]
  (let [options (-> opts
                  kwargs-or-map->map
                  (validate-options cache)
                  wash
                  (extract-options Caching$CreateOption))]
    (.findOrCreate (component) name options)))

(set-valid-options! cache (boolify (opts->set Caching$CreateOption) :transactional))

(defn with-codec
  "Decorates `cache` with the codec named by `codec`.

  Takes a cache and a keyword denoting a codec, and returns a new
  cache that applies that codec as entries are written/read to/from
  the passed cache. This is typically necessary only when non-clojure
  clients are sharing the cache. It's required if you wish to store
  nil keys or values. The provided codecs are `:edn` and `:json` (the
  latter requires you to depend on `cheshire`), and you can also use
  any other codecs you may have registered."
  [cache codec]
  (.withCodec (component)
    cache
    (lookup-codec codec)))

(defn with-expiration
  "Decorates `cache` with the given expiration options.

  The return value is a cache that will delegate any writes to the
  passed cache according to these options:

   * :ttl - the max time the entry will live before expiry [-1]
   * :idle - the time after which an entry will expire if not accessed [-1]

  Options may be passed as either keyword arguments or in a map.

  Negative values imply \"unlimited\". Values may be a number of
  milliseconds, a period keyword, or multiplier/keyword pairs, e.g.
  `[1 :hour 20 :minutes]`. Both singular and plural versions of
  :second, :minute, :hour, :day, and :week are valid period keywords.

  Expiration for existing entries in the passed cache will not be
  affected."
  [cache & options]
  (let [options (-> options
                  kwargs-or-map->map
                  wash)]
    (.withExpiration (component)
      cache
      (:ttl options -1)
      (:idle options -1))))

(defn swap-in!
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
  "Removes a cache, allowing it to be reconfigured by the [[cache]] function."
  [cache-or-name]
  (if (instance? org.infinispan.Cache cache-or-name)
    (.stop (component) (.getName ^org.infinispan.Cache cache-or-name))
    (.stop (component) (name cache-or-name))))

(defn builder
  "For advanced use, call this function to obtain a \"fluent\"
  [ConfigurationBuilder](https://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/configuration/cache/ConfigurationBuilder.html).
  Set the desired options, and invoke its build method, the result
  from which can be passed via the :configuration option of the
  [[cache]] function. For example:

  ```
  (let [config (.. (builder :ttl [30 :minutes])
                  clustering l1 (enabled true) build)]
    (cache \"L1\" :configuration config))
  ```

  Note that builder takes the same options as [[cache]] so you can
  concisely initialize it with Clojure, and use Java interop to set
  the more esoteric options"
  [& options]
  (Config/builder (-> options
                    kwargs-or-map->map
                    (validate-options cache builder)
                    wash
                    (extract-options Caching$CreateOption)
                    (Options.))))

(defn add-listener!
  "Attaches a listener fn to `cache` to be notified on certain events.

  Infinispan's cache notifications API is based on Java annotations,
  which can be awkward in Clojure (and Java, for that matter).

  This function provides the ability to be notified of cache events
  via single-arity callback functions taking an [Infinispan
  Event](https://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/notifications/cachelistener/event/package-summary.html)
  instance.

  Event types are represented as keywords corresponding to the
  [Infinispan
  annotations](http://docs.jboss.org/infinispan/6.0/apidocs/org/infinispan/notifications/cachelistener/annotation/package-summary.html):

   * :cache-entries-evicted
   * :cache-entry-activated
   * :cache-entry-created
   * :cache-entry-invalidated
   * :cache-entry-loaded
   * :cache-entry-modified
   * :cache-entry-passivated
   * :cache-entry-removed
   * :cache-entry-visited
   * :data-rehashed
   * :topology-changed
   * :transaction-completed
   * :transaction-registered

  The callbacks are synchronous, i.e. invoked on the thread acting on
  the cache. For longer running callbacks, use a queue or some sort of
  asynchronous channel.

  The return value is a sequence of listener objects corresponding to
  the requested event types, which will be a subset of those returned
  from the cache's own `getListeners` method. These may be passed to the
  cache's `removeListener` method to turn off notifications."
  [^org.infinispan.Cache cache f type & types]
  (let [results (doall (map (partial listener f) (conj types type)))]
    (doseq [i results]
      (.addListener cache i))
    results))
