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

(ns immutant.registry
  "Functions for working with Immutant's internal per-app registry."
  (:refer-clojure :exclude (get keys)))

(defonce ^{:private true} registry (atom {}))

(defn put
  "Store a value in the registry."
  [k v]
  (swap! registry assoc k v)
  v)

(defn get
  "Retrieve a value from the registry."
  [name]
  (clojure.core/get @registry name))

(defn keys
  "Return all the keys in the registry"
  []
  (clojure.core/keys @registry))
