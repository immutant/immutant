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

(ns immutant.runtime.bootstrap
  "Functions used in app bootstrapping."
  (:require [clojure.java.io          :as io]
            [clojure.walk             :as walk]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project   :as project]
            [cemerick.pomegranate     :as pomegranate])
  (:import [java.io   File FilenameFilter]
           [java.util ArrayList]))

(extend-type org.jboss.modules.ModuleClassLoader
  pomegranate/URLClasspath
  (can-modify? [this] false)
  (add-url [this _]))

(defn ^{:private true} stringify-symbol
  "Turns a symbol into a namspace/name string."
  [sym]
  (if (symbol? sym)
    (str (namespace sym) "/" (name sym))
    sym))

(defn ^{:private true} stringify-init-symbol
  "Turns the :init value into a string so we can use it in another runtime."
  [depth m]
  (update-in m (conj depth "init") stringify-symbol))

(defn ^{:internal true} read-descriptor
  "Reads a deployment descriptor and returns the resulting hash."
  [^File file]
  (stringify-init-symbol
   nil
   (walk/stringify-keys (read-string (slurp (.getAbsolutePath file))))))

;; TODO: support specifying profiles
(defn ^{:internal true} read-project
  "Reads a leiningen project.clj file in the given root dir."
  [app-root]
  (let [project-file (io/file app-root "project.clj")]
    (when (.exists project-file)
      (project/read (.getAbsolutePath project-file) [:default]))))

(defn ^{:internal true} read-and-stringify-project
  "Reads a leiningen project.clj file in the given root dir and stringifies the keys."
  [app-root]
  (when-let [project (walk/stringify-keys (read-project app-root))]
    (stringify-init-symbol ["immutant"] project)))

(defn ^{:private true} resolve-dependencies
  "Resolves dependencies from the lein project. It currently just delegates to leiningen-core."
  [project]
  (when project
    (classpath/resolve-dependencies :dependencies project)))

(defn ^{:internal true} lib-dir
  "Resolve the library dir for the application."
  [^File app-root]
  (io/file (:library-path (read-project app-root)
                          (str (.getAbsolutePath app-root) "/lib"))))

(defn ^{:private true} resource-paths-from-project
  "Resolves the resource paths (in the AS7 usage of the term) for a leiningen application. Handles
lein1/lein2 differences for project keys that changed from strings to vectors."
  [project]
  (remove nil?
          (flatten
           (map project [:compile-path   ;; lein1 and 2
                         :resources-path ;; lein1
                         :resource-paths ;; lein2
                         :source-path    ;; lein1
                         :source-paths   ;; lein2
                         :native-path    ;; lein2
                         ]))))

(defn ^{:private true} resource-paths-for-projectless-app
  "Resolves the resource paths (in the AS7 usage of the term) for a non-leiningen application."
  [app-root]
  (map #(.getAbsolutePath (io/file app-root %))
       ["src" "resources" "classes" "native"]))

(defn ^{:internal true} resource-paths
  "Resolves the resource paths (in the AS7 usage of the term) for an application."
  [app-root]
  (if-let [project (read-project app-root)]
    (resource-paths-from-project project)
    (resource-paths-for-projectless-app app-root)))

(defn ^{:internal true} bundled-jars
  "Returns a vector of any jars that are bundled in the application's lib-dir."
  [app-root]
  (let [^File lib-dir (lib-dir app-root)]
    (if (.isDirectory lib-dir)
      (.listFiles lib-dir (proxy [FilenameFilter] []
                            (accept [_ ^String file-name]
                              (.endsWith file-name ".jar")))))))

(defn ^{:internal true} get-dependencies
  "Resolves the dependencies for an application. It concats bundled jars with any aether resolved
dependencies, with bundled jars taking precendence. If resolve-deps is false, dependencies aren't
resolved via aether and only bundled jars are returned."
  [app-root resolve-deps]
  (let [bundled (bundled-jars app-root)
        bundled-jar-names (map (fn [^File f] (.getName f)) bundled)]
    (concat
     bundled
     (when resolve-deps
       (filter (fn [^File f] (not (some #{(.getName f)} bundled-jar-names)))
               (resolve-dependencies (read-project app-root)))))))
