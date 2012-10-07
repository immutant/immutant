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
            [immutant.utilities         :as util]
            [clojure.java.io            :as io])
  (:import org.projectodd.polyglot.core.util.ResourceLoaderUtil))

(defn ^:private reset-classloader-resources [resources]
  (ResourceLoaderUtil/refreshAndRelinkResourceLoaders
   clojure.lang.Var
   (map (fn [r] (ResourceLoaderUtil/createLoaderSpec r)) resources)
   false))

(defn ^:private unmount-resources
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

(defn ^:private mount-paths
  "Mounts the given paths and returns a collection of the resulting resources."
  [paths]
  (let [mounter (reg/fetch "resource-mounter")]
    (map #(.mount mounter %) paths)))

(defn ^:private read-project
  "Reads the lein project in the current app dir."
  []
  (boot/read-project (util/app-root) 
                     (:lein-profiles (reg/fetch :config))))

(defn ^:private get-dependency-paths [project]
  (mount-paths
   (boot/get-dependencies project true)))

(defn ^:private absolutize-paths [paths]
  (map (fn [p]
         (let [f (io/file p)]
           (if (.isAbsolute f)
             (.getAbsolutePath f)
             (.getAbsolutePath (io/file (util/app-root) f)))))
       paths))

(defn ^:private get-project-paths [project]
  (map #(ResourceLoaderUtil/createResourceRoot % true)
       (->> (boot/resource-paths-from-project project)
            (boot/add-default-lein1-paths (util/app-root))
            (absolutize-paths))))

(defn ^:private get-existing-resources []
  (remove nil?
          (map #(.getResource % "/")
               (ResourceLoaderUtil/getExistingResourceLoaders
                clojure.lang.Var))))

(defn current-project
  "Returns the map representing the currently active leiningen project.
This will be the last project reloaded by reload-project!, or the map read
from project.clj when the application was deployed reload-project! has yet
 to be called."
  []
  (reg/fetch :project))

(defn reload-project!
  "Resets the application's class loader to provide the paths and dependencies in the
from the given project. If no project is provided, the project.clj for the appplication
is loaded from disk. Returns the project map. This should never be used in production.
Works only under Clojure 1.4 or newer. (beta)"
  ([]
     (reload-project! (read-project)))
  ([project]
     (reg/put :project project)
     (let [new-resources (concat
                          (get-dependency-paths project)
                          (get-project-paths project))
           keeper-resources (unmount-resources
                             (get-existing-resources))]
       (reset-classloader-resources (concat new-resources keeper-resources)))
     project))

(defn merge-dependencies!
  "Merges in the given dependencies into the currently active project's dependency set
and resets the application's class loader to provide the paths and dependencies from that
project (via reload-project!). Returns the project map. This should never be used in production.
Works only under Clojure 1.4 or newer. (beta)"
  [& coords]
  (reload-project!
   (-> (current-project)
       (update-in [:dependencies] #(set (concat % (remove string? coords))))
       (update-in [:source-paths] #(concat % (filter string? coords))))))
