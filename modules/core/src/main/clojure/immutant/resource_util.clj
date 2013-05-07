;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc immutant.resource-util
    "Classpath resource management utils."
    (:require [immutant.registry     :as registry]
              [clojure.java.io       :as io])
    (:import org.projectodd.polyglot.core.util.ResourceLoaderUtil))

(defn ^:internal unmount-resources
  "Attempts to unmount the given resources. Returns a collection of
the resources that weren't unmounted."
  [resources]
  (let [mounter (registry/get "resource-mounter")]
    (reduce (fn [acc resource]
              (if (and resource (.unmount mounter (.getURL resource)))
                acc
                (conj acc resource)))
            []
            resources)))

(defn ^:internal mount-paths
  "Mounts the given paths and returns a collection of the resulting resources."
  [paths]
  (let [mounter (registry/get "resource-mounter")]
    (map #(.mount mounter (io/file %)) paths)))

(defn ^:internal reset-classloader-resources [resources]
  (ResourceLoaderUtil/refreshAndRelinkResourceLoaders
   clojure.lang.Var
   (map (fn [r] (ResourceLoaderUtil/createLoaderSpec r)) resources)
   false))

(defn ^:internal get-existing-resources []
  (remove nil?
          (map #(.getResource % "/")
               (ResourceLoaderUtil/getExistingResourceLoaders
                clojure.lang.Var))))
