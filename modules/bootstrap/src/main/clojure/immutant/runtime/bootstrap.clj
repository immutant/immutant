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

(ns ^{:no-doc true} immutant.runtime.bootstrap
  "Functions used in app bootstrapping. Should not be used in an app runtime."
  (:require [clojure.java.io          :as io]
            [clojure.walk             :as walk]
            [clojure.set              :as set]
            [clojure.tools.logging    :as log]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project   :as project])
  (:use immutant.runtime-util))

(defn ^{:internal true} read-project
  "Reads a leiningen project.clj file in the given root dir."
  [app-root profiles]
  (let [project-file (io/file app-root "project.clj")]
    (when (.exists project-file)
      (let [normalized-profiles (normalize-profiles profiles)
            project (project/read
                     (.getAbsolutePath project-file)
                     normalized-profiles)
            other-profiles (set (get-in project [:immutant :lein-profiles]))]
        (if (or (seq profiles) (not (seq other-profiles)))
          project
          (-> project
              (project/unmerge-profiles
               (set/difference normalized-profiles
                               other-profiles))
              (project/merge-profiles
               (set/difference other-profiles
                               normalized-profiles))))))))

(defn ^{:internal true} read-project-to-string
  "Returns the project map as a pr string with metadata so it can be moved across runtimes."
  [app-root profiles]
  (->> (read-project app-root profiles)
       (walk/postwalk (vary-meta dissoc :reduce)) ;; reduce points to a fn, so won't serialize
       pr-str-with-meta))

(defn ^{:internal true} read-full-app-config
  "Returns the full configuration for an app. This consists of the :immutant map
from project.clj (if any) with the contents of the descriptor map merged onto it (if any). Returns
nil if neither are available."
  [descriptor-file app-root]
  (let [from-descriptor (and descriptor-file
                             (read-descriptor descriptor-file))
        from-project (:immutant (read-project app-root (:lein-profiles from-descriptor)))]
    (merge {} from-project from-descriptor)))

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
    (project/load-certificates project)
    (try
      (->> (update-in project [:dependencies]
                      (fn [deps] (remove #(and
                                           (= "org.immutant" (namespace (first %)))
                                           (.startsWith (name (first %)) "immutant"))
                                        deps)))
           (classpath/resolve-dependencies :dependencies))
      (catch clojure.lang.ExceptionInfo e
        (log/error "The above resolution failure(s) prevented any maven dependency resolution. None of the dependencies listed in project.clj will be loaded from the local maven repository.")
        nil))))


(defn ^{:internal true} resource-paths
  "Resolves the resource paths (in the AS7 usage of the term) for an application."
  [app-root profiles]
  (if-let [project (read-project app-root profiles)]
    (add-default-lein1-paths app-root
                             (resource-paths-from-project project))
    (resource-paths-for-projectless-app app-root)))

(defn ^{:internal true} get-dependencies
  "Resolves the dependencies for an application. It concats bundled jars with any aether resolved
dependencies, with bundled jars taking precendence. If resolve-deps is false, dependencies aren't
resolved via aether and only bundled jars are returned. This strips any org.immutant/immutant-*
deps from the list before resolving to prevent conflicts with internal jars."
  ([app-root profiles resolve-deps?]
     (get-dependencies (or (read-project app-root profiles)
                           {:root app-root})
                       resolve-deps?))
  ([project resolve-deps?]
     (let [bundled (bundled-jars project)
           bundled-jar-names (map (fn [f] (.getName f)) bundled)]
       (concat
        bundled
        (when resolve-deps?
          (filter (fn [f] (not (some #{(.getName f)} bundled-jar-names)))
                  (resolve-dependencies project)))))))

(defn ^:internal get-dependencies-from-project-string-as-string
  [project-as-string resolve-deps?]
  (pr-str-with-meta
    (map #(.getAbsolutePath %)
         (get-dependencies (read-string project-as-string) resolve-deps?))))
