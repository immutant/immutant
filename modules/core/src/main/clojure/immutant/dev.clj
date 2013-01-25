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

(ns immutant.dev
  "Functions useful for repl based development inside an Immutant container. They
shouldn't be used in production."
  (:require [immutant.registry     :as registry]
            [immutant.util         :as util]
            [immutant.runtime-util :as runtime]
            [clojure.java.io       :as io])
  (:import org.immutant.core.ApplicationBootstrapProxy
           org.projectodd.polyglot.core.util.ResourceLoaderUtil))

(defn ^:private reset-classloader-resources [resources]
  (ResourceLoaderUtil/refreshAndRelinkResourceLoaders
   clojure.lang.Var
   (map (fn [r] (ResourceLoaderUtil/createLoaderSpec r)) resources)
   false))

(defn ^:private unmount-resources
  "Attempts to unmount the given resources. Returns a collection of the resources
that weren't unmounted."
  [resources]
  (let [mounter (registry/get "resource-mounter")]
    (reduce (fn [acc resource]
              (if (and resource (.unmount mounter (.getURL resource)))
                acc
                (conj acc resource)))
            []
            resources)))

(defn ^:private mount-paths
  "Mounts the given paths and returns a collection of the resulting resources."
  [paths]
  (let [mounter (registry/get "resource-mounter")]
    (map #(.mount mounter (io/file %)) paths)))

(defn ^:private read-project
  "Reads the lein project in the current app dir."
  []
  (read-string
   (ApplicationBootstrapProxy/readProjectAsString
    (util/app-root) 
    (map str (:lein-profiles (registry/get :config))))))

(defn ^:private get-dependency-paths [project]
  (mount-paths
   (read-string
    (ApplicationBootstrapProxy/getDependenciesAsString
     (runtime/pr-str-with-meta project)
     true))))

(defn ^:private absolutize-paths [paths]
  (map (fn [p]
         (let [f (io/file p)]
           (if (.isAbsolute f)
             (.getAbsolutePath f)
             (.getAbsolutePath (io/file (util/app-root) f)))))
       paths))

(defn ^:private get-project-paths [project]
  (map #(ResourceLoaderUtil/createResourceRoot % true)
       (->> (runtime/resource-paths-from-project project)
            (runtime/add-default-lein1-paths (util/app-root))
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
  (registry/get :project))


(defonce ^:private original-default-data-readers (if-let [d (util/try-resolve 'clojure.core/default-data-readers)] @d))

(defn ^:private load-data-readers []
  ;; The deps may have brought in data readers, so we load them.
  ;; Since *data-readers* is already bound, altering its root binding won't help,
  ;; so we instead muck with default-data-readers. From a functionality pov, it
  ;; should work the same for the user.

  ;; we have to do all the try-resolve muck so this will load under 1.3, but
  ;; there's probably a better way to do it
  (if original-default-data-readers
    (alter-var-root (util/try-resolve 'clojure.core/default-data-readers)
                    (fn [_]
                      (reduce (util/try-resolve 'clojure.core/load-data-reader-file)
                              original-default-data-readers
                              ((util/try-resolve 'clojure.core/data-reader-urls)))))))

(defn reload-project!
  "Resets the application's class loader to provide the paths and dependencies in the
from the given project. If no project is provided, the project.clj for the appplication
is loaded from disk. If used under clojure > 1.3.0, this will also make any new data
readers from the dependencies available. Returns the project map. This should never
be used in production. (beta)"
  ([]
     (reload-project! (read-project)))
  ([project]
     (registry/put :project project)
     (let [new-resources (concat
                          (get-dependency-paths project)
                          (get-project-paths project))
           keeper-resources (unmount-resources
                             (get-existing-resources))]
       (reset-classloader-resources (concat new-resources keeper-resources)))
     (load-data-readers)
     project))

(defn add-dependencies!
  "Adds the given dependencies into the currently active project's dependency set
and resets the application's class loader to provide the paths and dependencies from that
project (via reload-project!). Each dep can either be a lein coordinate ('[foo-bar \"0.1.0\"])
or a path (as a String) to be added to :source-paths. Returns the project map. This should
never be used in production. (beta)"
  [& deps]
  (reload-project!
   (-> (current-project)
       (update-in [:dependencies] #(set (concat % (remove string? deps))))
       (update-in [:source-paths] #(concat % (filter string? deps))))))

(defn remove-lib
  "Borrowed from tools.namespace"
  [lib]
  (remove-ns lib)
  (dosync (alter @#'clojure.core/*loaded-libs* disj lib)))


