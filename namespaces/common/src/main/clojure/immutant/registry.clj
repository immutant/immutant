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

(ns immutant.registry
  "Functions for working with Immutant's internal per-app registry."
  (:refer-clojure :exclude (get keys))
  (:import org.jboss.msc.service.ServiceName))

(defonce ^{:private true} registry (atom {}))
(defonce ^{:private true} msc-registry (atom nil))

(defn set-msc-registry [v]
  (reset! msc-registry v))

(defn ^{:private true} get-from-msc [name]
  (if @msc-registry
    (try
      (let [key (if (string? name) (ServiceName/parse name) name)
            controller (.getService @msc-registry key)]
        (and controller (.getValue controller)))
      (catch ClassCastException _))))
  
(defn put
  "Store a value in the registry."
  [k v]
  (swap! registry assoc k v)
  v)

(defn get
  "Retrieve a value from the registry."
  [name]
  (or (clojure.core/get @registry name) (get-from-msc name)))

(defn service-names
  "Return the JBoss MSC service names"
  []
  (map (memfn getCanonicalName) (.getServiceNames @msc-registry)))

(defn keys
  "Return all the keys in the registry"
  []
  (concat (clojure.core/keys @registry)
          (service-names)))
