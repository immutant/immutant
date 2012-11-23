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

(ns immutant.integs
  (:use [clojure.test]
        [fntest.core]
        [clojure.tools.namespace :only (find-namespaces-in-dir)])
  (:require [clojure.java.io               :as io]
            [clojure.string                :as string]
            [clojure.walk                  :as walk]
            [cemerick.pomegranate.aether   :as aether]
            [leiningen.core.project        :as project]
            [immutant.deploy-tools.archive :as archive]))

(def ^{:dynamic true} *current-clojure-version* nil)

(def clojure-versions
  (set (string/split (System/getProperty "versions") #",")))

(defn find-app-dirs
  ([dir]
     (find-app-dirs [] dir))
  ([app-dirs dir]
     (if (.exists (io/file dir "src"))
       (conj app-dirs dir)
       (reduce find-app-dirs app-dirs (.listFiles dir)))))

(def jar-filter
  (proxy [java.io.FilenameFilter] []
    (accept [^java.io.File _ ^String name]
      (boolean (re-find #"clojure-\d.*\.jar" name)))))

(defn remove-clojure-jars
  "removes clojure jars from the apps' lib dirs"
  []
  (doseq [lib-dir (map #(io/file % "lib")
                       (find-app-dirs (io/file (System/getProperty "user.dir") "apps")))]
    (doseq [jar (.listFiles lib-dir jar-filter)]
      (io/delete-file jar))))

(defn update-project-clj [clojure-dep app-dir]
  (let [project (io/file app-dir "project.clj")]
    (io/copy
     (pr-str
      (walk/postwalk #(if (and (vector? %) (= 'org.clojure/clojure (first %)))
                        clojure-dep
                        %) (read-string (slurp project))))
     project)))

(defn set-version
  "adds the proper version of the clojure jar to each app"
  [version]
  (let [app-dirs (find-app-dirs (io/file (System/getProperty "user.dir") "target/apps"))
        coord ['org.clojure/clojure version]]
    (if-let [clojure-jar (first (aether/dependency-files (aether/resolve-dependencies :coordinates [coord])))]
      (doseq [app-dir app-dirs]
        (if (.exists (io/file app-dir "project.clj"))
          (update-project-clj coord app-dir)
          (let [lib-dir (io/file app-dir "lib")]
            (.mkdir lib-dir)
            (io/copy clojure-jar (io/file lib-dir (.getName clojure-jar))))))
      (throw (Exception. (str "Failed to resolve clojure " version))))))

(defn for-each-version
  "Invokes the given fn with the integ apps set to use each clojure version."
  [f]
  (doseq [version clojure-versions]
    (try
      (set-version version)
      (println "\n>>>>" (str \[ (.toString (java.util.Date.)) \])
               "Running integs with clojure" version )
      (binding [*current-clojure-version*
                (let [v (re-find #"(\d+)\.(\d+)\.(\d+)" version)]
                  (zipmap [:full :major :minor :incremental]
                          (conj (map #(Integer/parseInt %) (rest v)) (first v))))]
        (time (f)))
      (println "\n<<<<" (str \[ (.toString (java.util.Date.)) \])
               "Finished integs with clojure"
               version "\n")
      (finally (remove-clojure-jars)))))

(defn ns-from-property []
  "Gets the namespace to test from the system property 'ns'"
  (let [value (System/getProperty "ns")]
    (if (not (empty? value))
      (map #(let [ns (read-string %)]
              (if (re-find #"^immutant\.integs\." (name ns))
                ns
                (symbol (str "immutant.integs." (name ns)))))
           (string/split value #",")))))

(defn generate-archives [dest-dir & app-dirs]
  (doseq [app app-dirs]
    (println "Generating archive for" app)
    (archive/create (project/read (str app "/project.clj"))
                    (io/file app)
                    (io/file dest-dir)
                    {})))

(let [integs (io/file (.getParentFile (io/file *file*)) "integs")
      namespaces (or (ns-from-property) (find-namespaces-in-dir integs))]
  (generate-archives "target/apps" "apps/ring/basic-ring")
  (println "\nTesting namespaces:" namespaces)
  (apply require namespaces)
  (when-not *compile-files*
    (let [results (atom [])
          report-orig report]
      (binding [fntest.jboss/*home* "./target/integ-dist/jboss"
                fntest.jboss/*descriptor-root* "target/.descriptors"
                report (fn [x] (report-orig x)
                         (swap! results conj (:type x)))]
        (println "\n>>>> Testing against" clojure-versions "\n")
        (for-each-version
         #(with-jboss (partial apply run-tests namespaces) :lazy)))
      (shutdown-agents)
      (System/exit (if (empty? (filter #{:fail :error} @results)) 0 -1)))))
