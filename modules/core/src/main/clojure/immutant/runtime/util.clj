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

(ns ^{:no-doc true} immutant.runtime.util
  "Util functions that are boostrap related, but are safe to use in any runtime."
  (:require [clojure.java.io          :as io]
            [clojure.walk             :as walk]
            [clojure.string           :as str])
  (:import [java.io File FilenameFilter]))

(defn ^{:internal true} stringify-symbol
  "Turns a symbol into a namespace/name string."
  [sym]
  (if (symbol? sym)
    (str (namespace sym) "/" (name sym))
    sym))

(defn ^:private updatifier
  "Generates a function to update map values with a given function with an optional path."
  [key f]
  (fn
    ([m]
       (update-in m [key] f))
    ([m path]
       (update-in m (conj path key) f))))

(def ^{:internal true
       :doc "Turns the :init value into a string so we can use it in another runtime."}
  stringify-init-symbol
  (updatifier "init" stringify-symbol))

(def ^{:internal true
       :doc "Turns the :lein-profiles values into strings so we can use them in another runtime."}
  stringify-lein-profiles
  (updatifier "lein-profiles" #(map str %)))

(defn ^{:internal true} pr-str-with-meta [x]
   (binding [*print-meta* true]
     (pr-str x)))

(defn ^{:internal true} read-descriptor
  "Reads a deployment descriptor and returns the resulting map."
  [^File file]
  (load-string (slurp (.getAbsolutePath file))))

(defn ^{:internal true} read-and-stringify-descriptor
  "Reads a deployment descriptor and returns the resulting stringified map."
  [^File file]
  (-> (read-descriptor file)
      walk/stringify-keys 
      stringify-init-symbol
      stringify-lein-profiles))

(defn ^{:internal true} normalize-profiles [profiles]
  (set (if (seq profiles)
         (map #(if (keyword? %)
                 %
                 (keyword (str/replace % ":" "")))
              profiles)
         [:default])))

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

(defn ^{:internal true} resource-paths-for-projectless-app
  "Resolves the resource paths (in the AS7 usage of the term) for a non-leiningen application."
  [app-root]
  (map #(.getAbsolutePath (io/file app-root %))
       ["src" "resources" "classes" "native"]))

(defn ^{:internal true} add-default-lein1-paths
  "lein1 assumes classes/, 2 assumes target/classes/, so getting it from the project will return the wrong default for lein1 projects."
  [app-root paths]
  (conj paths
        (.getAbsolutePath (io/file app-root "classes"))))

(defn ^{:internal true} bundled-jars
  "Returns a set of any jars that are bundled in the application's lib-dir."
  [project]
  (let [^File lib-dir (lib-dir project)]
    (set
     (if (.isDirectory lib-dir)
       (.listFiles lib-dir (proxy [FilenameFilter] []
                             (accept [_ ^String file-name]
                               (.endsWith file-name ".jar"))))))))


