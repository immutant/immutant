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

(ns immutant.caching.core-memoize
  "Use an Infinispan cache as a clojure.core.memoize/PluggableMemoization"
  (:require [immutant.caching          :refer [cache]]
            [immutant.internal.options :refer [validate-options]]
            [clojure.core.memoize      :refer [build-memoizer]]
            [clojure.core.cache :as cc]
            immutant.caching.core-cache)
  (:import clojure.core.memoize.PluggableMemoization))

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

(defn memo
  "Memoize a function by associating its arguments with return values
   stored in a possibly-clustered Infinispan-backed cache. Other than
   the function to be memoized, arguments are the same as for
   {{immutant.caching/cache}}"
  [f name & {:as options}]
  (let [cache (cache name (validate-options options cache memo))]
    (build-memoizer
      #(PluggableMemoization. % (DelayedCache. cache (atom {})))
      f)))
