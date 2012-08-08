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

(ns immutant.dev
  "Functions useful for repl based development inside an Immutant container. They
shouldn't be used in production."
  (:require [immutant.runtime.bootstrap :as boot]
            [immutant.registry          :as reg]
            [immutant.utilities         :as util])
  (:import org.projectodd.polyglot.core.util.ResourceLoaderUtil))

(defn ^{:private true} reset-classloader-resources [resources]
  (ResourceLoaderUtil/refreshAndRelinkResourceLoaders
   clojure.lang.Var
   (map (fn [r] (ResourceLoaderUtil/createLoaderSpec r)) resources)
   false))

(defn ^{:private true} unmount-resources
  "Attempts to unmount the given resources. Returns a collection of the resources
that weren't unmounted."
  [resources]
  (let [mounter (reg/fetch "resource-mounter")]
    (reduce (fn [acc resource]
              (if (and resource (.unmount mounter (.getURL resource)))
                acc
                (conj acc resource)))
            []
            resources)))

(defn ^{:private true} mount-paths
  "Mounts the given paths and returns a collection of the resulting resources."
  [paths]
  (let [mounter (reg/fetch "resource-mounter")]
    (map #(.mount mounter %) paths)))

(defn reload-dependencies!
  "Rereads the dependencies for the current application and resets the application's
ClassLoader to provide those dependencies. This is beta, and either way should not be
used in production."
  []
  (let [new-resources (mount-paths
                       (boot/get-dependencies (util/app-root) true
                                              (:lein-profiles (reg/fetch :config))))
        keeper-resources (unmount-resources
                          (remove nil?
                                  (map #(.getResource % "/")
                                       (ResourceLoaderUtil/getExistingResourceLoaders clojure.lang.Var))))]
    (reset-classloader-resources (concat new-resources keeper-resources))))
