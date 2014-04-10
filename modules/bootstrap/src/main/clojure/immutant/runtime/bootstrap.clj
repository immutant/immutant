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

(ns ^:no-doc immutant.runtime.bootstrap
  "Functions used in app bootstrapping. Should not be used in an app runtime."
  (:require [clojure.java.io                :as io]
            [clojure.walk                   :as walk]
            [clojure.set                    :as set]
            [leiningen.core.classpath       :as classpath]
            [leiningen.core.project         :as project]
            [robert.hooke                   :as hooke]
            [immutant.dependency-exclusions :as depex]
            [immutant.logging               :as log])
  (:use immutant.runtime-util)
  (:import clojure.lang.DynamicClassLoader
           org.immutant.bootstrap.ApplicationBootstrapUtils))

(def ^:private dedicated-classloaders (atom {}))

(defn ^:private classloader-key [project-or-app-root]
  (let [root (:root project-or-app-root project-or-app-root)
        root (if (string? root) (io/file root) root)]
    (.getCanonicalPath root)))

(defn ^:internal clear-dedicated-classloader [app-root]
  (swap! dedicated-classloaders dissoc
         (classloader-key app-root)))

(defn ^:private tccl
  ([]
     (.getContextClassLoader (Thread/currentThread)))
  ([cl]
     (.setContextClassLoader (Thread/currentThread) cl)))

(defn ^:private add-plugin-deps
  "Adds any jars in project-root/plugin-deps to the given classloader.
   This allows plugins to be included in archives so they don't have
  to be resolved at runtime."
  [cl root]
  (doseq [lib (->> (io/file root "plugin-deps")
                file-seq
                (filter #(.endsWith (.getName %) ".jar")))]
    (.addURL cl (-> lib .toURI .toURL))))

(defn ^:private dedicated-classloader [project-or-app-root]
  (let [key (classloader-key project-or-app-root)]
    (if-let [cl (@dedicated-classloaders key)]
      cl
      (let [cl (DynamicClassLoader. (tccl))]
        (swap! dedicated-classloaders assoc key cl)
        (add-plugin-deps cl key)
        cl))))

(def ^:private ^:dynamic *allow-dependency-resolution* true)

(defmacro with-dependency-resolution [cond & body]
  `(binding [*allow-dependency-resolution* ~cond]
    ~@body))

(defn ^:private add-classpath-to-immediate-cl
  ;; Pomegranate defaults to adding dependencies to the highest
  ;; dynamic CL it can find, which will be above the cl we want it to
  ;; use. This hook forces it to add to the tccl
  ([f artifact cl]
     (f artifact cl))
  ([f artifact]
     (f artifact (tccl))))

(defn ^:private resolve-dependencies-without-memoization
  ;; leiningen.core.classpath/resolve-dependencies is memoized, but we
  ;; need to be able to invoke multiple times. This hook breaks the
  ;; memoization. This also allows turning off resolution altogether
  ;; by binding *allow-dependency-resolution*.
  [f key project & rest]
  (when *allow-dependency-resolution*
    (apply f key project
      (conj rest :timestamp (System/nanoTime)))))

(defmacro ^:private in-dedicated-classloader [project-or-app-root & body]
  `(let [orig-cl# (tccl)]
     (try
       (tccl (dedicated-classloader ~project-or-app-root))
       (hooke/with-scope
         (hooke/add-hook
           #'cemerick.pomegranate/add-classpath
           #'add-classpath-to-immediate-cl)
         (hooke/add-hook
           #'leiningen.core.classpath/resolve-dependencies
           #'resolve-dependencies-without-memoization)
         ~@body)
       (finally
         (tccl orig-cl#)))))

(def ^:internal read-project
  (memoize
    (fn [app-root profiles escape-memoization?]
      (in-dedicated-classloader
        app-root
        (let [project-file (io/file app-root "project.clj")]
          (when (.exists project-file)
            (let [normalized-profiles (normalize-profiles profiles)
                  project (-> project-file
                            .getAbsolutePath
                            (project/read normalized-profiles)
                            project/init-project)
                  other-profiles (set (get-in project [:immutant :lein-profiles]))]
              (if (or (seq profiles) (not (seq other-profiles)))
                project
                (-> project
                  (project/unmerge-profiles
                    (set/difference normalized-profiles
                      other-profiles))
                  (project/merge-profiles
                    (set/difference other-profiles
                      normalized-profiles)))))))))))

(defn ^:private remove-reduce-metadata [v]
  (if (fn? (:reduce v))
    (dissoc v :reduce)
    v))

(defn ^:private remove-checkout-deps-paths-fn [v]
  (if (:checkout-deps-shares v)
    (update-in v [:checkout-deps-shares]
      #(vec (remove var? %)))
    v))

(defn postwalk-with-meta [f meta-f form]
  (walk/postwalk
    #(if (instance? clojure.lang.IObj %)
       (with-meta (f %) (postwalk-with-meta meta-f meta-f (meta %)))
       (f %))
    form))

(defn ^{:internal true} read-project-to-string
  "Returns the project map as a pr string with metadata so it can be
  moved across runtimes."
  [app-root profiles escape-memoization? resolve-plugin-deps?]
  (pr-str-with-meta
            (when-let [p (with-dependency-resolution resolve-plugin-deps?
                           (read-project app-root profiles
                             escape-memoization?))]
              (postwalk-with-meta
                remove-checkout-deps-paths-fn
                #(-> %
                   remove-reduce-metadata
                   remove-checkout-deps-paths-fn)
                p))))

(defn ^{:internal true} read-full-app-config
  "Returns the full configuration for an app. This consists of
   the :immutant map from project.clj (if any) with the contents of
   the internal descriptor map merged onto it (if any) followed by the
   descriptor map (if any). Returns {} if none are available."
   [descriptor-file app-root resolve-plugin-deps?]
   (let [external (read-descriptor descriptor-file)
         internal (read-descriptor (io/file app-root ".immutant.clj"))
         profiles (:lein-profiles external (:lein-profiles internal))]
     (merge {}
       (:immutant (with-dependency-resolution resolve-plugin-deps?
                    (read-project app-root profiles nil)))
       internal
       external)))

(defn ^{:internal true} read-and-stringify-full-app-config
  "Loads the full app config and stringifies the keys."
  [descriptor-file app-root resolve-plugin-deps?]
  (-> (read-full-app-config descriptor-file app-root resolve-plugin-deps?)
      walk/stringify-keys
      stringify-init-symbol
      stringify-lein-profiles))

(defn ^{:internal true} read-full-app-config-to-string
  "Returns the full configuration for an app as a pr string that can
  move across runtimes."
  [descriptor-file app-root resolve-plugin-deps?]
  (pr-str (read-full-app-config descriptor-file app-root resolve-plugin-deps?)))

(defn ^{:private true :testable true} resolve-dependencies
  "Resolves dependencies from the lein project. It delegates to leiningen-core, but attempts
to gracefully handle missing dependencies."
  [project]
  (when project
    (in-dedicated-classloader
     project
     (try
       (classpath/resolve-dependencies
        :dependencies
        (-> project
          depex/exclude-immutant-deps))
       (catch clojure.lang.ExceptionInfo e
         (log/error
          "The above resolution failure(s) prevented any maven dependency resolution. None of the dependencies listed in project.clj will be loaded from the local maven repository.")
         nil)))))


(defn ^:internal resource-paths-for-project
  [project]
  ;; get the classpath w/o the deps, since we resolve those
  ;; elsewhere.
  (in-dedicated-classloader
   project
   (-> project
       (dissoc :dependencies)
       classpath/get-classpath)))

(defn ^:internal resource-paths-for-project-string-as-string
  [project-as-string]
  (-> project-as-string
      read-string
      resource-paths-for-project
      pr-str-with-meta))

(defn ^{:internal true} resource-paths
  "Resolves the resource paths (in the AS7 usage of the term) for an application."
  [app-root profiles resolve-plugin-deps?]
  (if-let [project (with-dependency-resolution resolve-plugin-deps?
                     (read-project app-root profiles nil))]
    (resource-paths-for-project project)
    (resource-paths-for-projectless-app app-root)))

(defn ^{:internal true} get-dependencies
  "Resolves the dependencies for an application. It concats bundled
jars with any aether resolved dependencies, with bundled jars taking
precendence. If resolve-deps is false, dependencies aren't resolved
via aether and only bundled jars are returned. This strips any
org.immutant/immutant-* deps from the list before resolving to prevent
conflicts with internal jars."
  ([app-root profiles resolve-deps? resolve-plugin-deps?]
     (get-dependencies
       (or (with-dependency-resolution resolve-plugin-deps?
             (read-project app-root profiles nil))
         {:root app-root})
       resolve-deps?
       resolve-plugin-deps?))
  ([project resolve-deps? resolve-plugin-deps?]
     (let [bundled (bundled-jars project)
           bundled-jar-names (map (fn [f] (.getName f)) bundled)]
       (concat
         bundled
         (when resolve-deps?
           (filter (fn [f] (not (some #{(.getName f)} bundled-jar-names)))
             (resolve-dependencies
               (with-dependency-resolution resolve-plugin-deps?
                 (project/init-project project)))))))))

(defn ^:internal get-dependencies-from-project-string-as-string
  [project-as-string resolve-deps? resolve-plugin-deps?]
  (pr-str-with-meta
    (map #(.getAbsolutePath %)
      (get-dependencies (read-string project-as-string) resolve-deps? resolve-plugin-deps?))))
