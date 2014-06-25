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

(ns immutant.caching.core-cache
  "Extends org.infinispan.Cache to clojure.core.cache/CacheProtocol"
  (:require [clojure.core.cache :as core])
  (:import org.infinispan.Cache))

(extend-type org.infinispan.Cache
  core/CacheProtocol
  (lookup
    ([this key]
       (.get this key))
    ([this key not-found]
       (if (.containsKey this key)
               (.get this key)
               not-found)))
  (has? [this key]
    (.containsKey this key))
  (hit [this key]
    this)
  (miss [this key value]
    (.put this key value)
    this)
  (evict [this key]
    (.remove this key)
    this)
  (seed [this base]
    (when base (.putAll this base))
    this))
