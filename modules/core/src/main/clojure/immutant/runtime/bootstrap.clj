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
  (:require [clojure.java.io       :as io]
            [clojure.walk          :as walk]
            [clojure.set           :as set]
            [clojure.string        :as str]
            [clojure.tools.logging :as log]
            [cemerick.pomegranate  :as pomegranate]
            [immutant.utilities    :as util])
  (:import [java.io File FilenameFilter]
           java.util.ArrayList
           org.sonatype.aether.resolution.DependencyResolutionException))
(try
  (require 'leiningen.core.classpath)
  (require 'leiningen.core.project)
  (catch RuntimeException e
    (if (and (<= 1 (:major *clojure-version*))
             (> 4 (:minor *clojure-version*)))
      (println "WARNING: immutant.dev requires clojure 1.4 or greater and you've loaded it under clojure 1.3")
      (throw e))))


(defn ^{:private true} stringify-symbol
  "Turns a symbol into a namspace/name string."
  [sym]
  (if (symbol? sym)
    (str (namespace sym) "/" (name sym))
    sym))

(defn ^{:private true} updatifier
  "Generates a function to update map values with a given function with an optional path."
  [key f]
  (fn
    ([m]
       (update-in m [key] f))
    ([m path]
       (update-in m (conj path key) f))))

(def ^{:private true
       :doc "Turns the :init value into a string so we can use it in another runtime."}
  stringify-init-symbol
  (updatifier "init" stringify-symbol))

(def ^{:private true
       :doc "Turns the :lein-profiles values into strings so we can use them in another runtime."}
  stringify-lein-profiles
  (updatifier "lein-profiles" #(map str %)))

(defn ^{:internal true} read-descriptor
  "Reads a deployment descriptor and returns the resulting map."
  [^File file]
  (read-string (slurp (.getAbsolutePath file))))

(defn ^{:internal true} read-and-stringify-descriptor
  "Reads a deployment descriptor and returns the resulting stringified map."
  [^File file]
  (-> (read-descriptor file)
      walk/stringify-keys 
      stringify-init-symbol
      stringify-lein-profiles))

(defn ^{:private true
        :testable true}
  normalize-profiles [profiles]
  (set (if (seq profiles)
         (map #(if (keyword? %)
                 %
                 (keyword (str/replace % ":" "")))
              profiles)
         [:default])))

(defn ^{:internal true} read-project
  "Reads a leiningen project.clj file in the given root dir."
  [app-root profiles]
  (let [project-file (io/file app-root "project.clj")]
    (when (.exists project-file)
      (let [normalized-profiles (normalize-profiles profiles)
            project ((util/try-resolve 'leiningen.core.project/read)
                     (.getAbsolutePath project-file)
                     normalized-profiles)
            other-profiles (set (get-in project [:immutant :lein-profiles]))]
        (if (or (seq profiles) (not (seq other-profiles)))
          project
          (-> project
              ((util/try-resolve 'leiningen.core.project/unmerge-profiles)
               (set/difference normalized-profiles
                               other-profiles))
              ((util/try-resolve 'leiningen.core.project/merge-profiles)
               (set/difference other-profiles
                               normalized-profiles))))))))

(defn ^{:internal true} read-project-to-string
  "Returns the project map as a pr string with metadata so it can be moved across runtimes."
  [app-root profiles]
  (binding [*print-meta* true]
    (pr-str (read-project app-root profiles))))

(defn ^{:internal true} read-full-app-config
  "Returns the full configuration for an app. This consists of the :immutant map
from project.clj (if any) with the contents of the descriptor map merged onto it (if any). Returns
nil if neither are available."
  [descriptor-file app-root]
  (let [from-descriptor (and descriptor-file
                          (read-descriptor descriptor-file))
        from-project (:immutant (read-project app-root (:lein-profiles from-descriptor)))]
    (merge from-project from-descriptor)))

(defn ^{:internal true} read-and-stringify-full-app-config
  "Loads the full app config and stringifies the keys."
  [descriptor-file app-root]
  (-> (read-full-app-config descriptor-file app-root)
      walk/stringify-keys
      stringify-init-symbol
      stringify-lein-profiles))

(defn ^{:internal true} read-full-app-config-to-string
  "Returns the full configuration for an app as a pr string that can move across runtimes."
  [descriptor-file app-root]
  (pr-str (read-full-app-config descriptor-file app-root)))

(defn ^{:private true :testable true} resolve-dependencies
  "Resolves dependencies from the lein project. It delegates to leiningen-core, but attempts
to gracefully handle missing dependencies."
  [project]
  (when project
    ((util/try-resolve 'leiningen.core.project/load-certificates) project)
    (try
      ((util/try-resolve 'leiningen.core.classpath/resolve-dependencies) :dependencies project)
      (catch clojure.lang.ExceptionInfo e
        (log/error "The above resolution failure(s) prevented any maven dependency resolution. None of the dependencies listed in project.clj will be loaded from the local maven repository.")
        nil))))

(defn ^{:internal true} lib-dir
  "Resolve the library dir for the application."
  [^File project]
  (io/file (:library-path project
                          (io/file (:root project) "lib"))))

(defn ^{:internal true} resource-paths-from-project
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

(defn ^{:internal true} add-default-lein1-paths
  "lein1 assumes classes/, 2 assumes target/classes/, so getting it from the project will return the wrong default for lein1 projects."
  [app-root paths]
  (conj paths
        (.getAbsolutePath (io/file app-root "classes"))))

(defn ^{:internal true} resource-paths
  "Resolves the resource paths (in the AS7 usage of the term) for an application."
  [app-root profiles]
  (if-let [project (read-project app-root profiles)]
    (add-default-lein1-paths app-root
                             (resource-paths-from-project project))
    (resource-paths-for-projectless-app app-root)))

(defn ^{:internal true} bundled-jars
  "Returns a set of any jars that are bundled in the application's lib-dir."
  [project]
  (let [^File lib-dir (lib-dir project)]
    (set
     (if (.isDirectory lib-dir)
       (.listFiles lib-dir (proxy [FilenameFilter] []
                             (accept [_ ^String file-name]
                               (.endsWith file-name ".jar"))))))))

(defn ^{:internal true} get-dependencies
  "Resolves the dependencies for an application. It concats bundled jars with any aether resolved
dependencies, with bundled jars taking precendence. If resolve-deps is false, dependencies aren't
resolved via aether and only bundled jars are returned."
  ([app-root profiles resolve-deps?]
     (get-dependencies (or (read-project app-root profiles)
                           {:root app-root})
                       resolve-deps?))
  ([project resolve-deps?]
     (let [bundled (bundled-jars project)
           bundled-jar-names (map (fn [^File f] (.getName f)) bundled)]
       (concat
        bundled
        (when resolve-deps?
          (filter (fn [^File f] (not (some #{(.getName f)} bundled-jar-names)))
                  (resolve-dependencies project)))))))
