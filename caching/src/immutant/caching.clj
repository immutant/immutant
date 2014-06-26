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
  "Optionally persistent ConcurrentMap implementations"
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
  "Find a cache by name or create one with the passed options"
  (^org.infinispan.Cache [name]
    (cache name {}))
  (^org.infinispan.Cache [name k v & kvs]
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
  "Use the codec when storing/retrieving elements in the cache"
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
  "Removes a cache"
  [cache-or-name]
  (if (instance? org.infinispan.Cache cache-or-name)
    (.stop (component) (.getName cache-or-name))
    (.stop (component) (name cache-or-name))))

(defn builder
  "Returns a fluent infinispan configuration builder"
  [& options]
  (Config/builder (-> options
                    kwargs-or-map->map
                    keywordize-keys
                    (validate-options cache builder)
                    wash
                    (extract-options Caching$CreateOption)
                    (Options.))))
