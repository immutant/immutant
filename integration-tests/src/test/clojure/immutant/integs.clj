;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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
  (:use [clojure.test])
  (:use [fntest.core])
  (:use [clojure.tools.namespace :only (find-namespaces-in-dir)])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(def clojure-versions
  (set (string/split (System/getProperty "integ.clojure.versions") #",")))

(defn find-app-dirs
  ([dir]
     (find-app-dirs [] dir))
  ([app-dirs dir]
      (if (.exists (io/file dir "project.clj"))
        (conj app-dirs dir)
        (reduce find-app-dirs app-dirs (.listFiles dir)))))

(def app-lib-dirs
  (map #(io/file % "lib")
       (find-app-dirs (io/file (System/getProperty "user.dir") "apps"))))

(def jar-filter
  (proxy [java.io.FilenameFilter] []
    (accept [^java.io.File _ ^String name]
      (boolean (re-find #"clojure-\d.*\.jar" name)))))

(defn remove-clojure-jars
  "removes clojure jars from the apps' lib dirs"
  []
  (doseq [lib-dir app-lib-dirs]
    (doseq [jar (.listFiles lib-dir jar-filter)]
      (io/delete-file jar))))

(defn set-version
  "adds the proper version of the clojure jar to each app"
  [version]
  (let [jar-name (str "clojure-" version ".jar")]
    (if-let [jar (io/resource jar-name)]
      (doseq [lib-dir app-lib-dirs]
        (io/copy (io/file jar) (io/file lib-dir jar-name))))))

(defn for-each-version
  "Runs the integ apps under each clojure version"
  [namespaces]
  (doseq [version clojure-versions]
    (try
      (set-version version)
      (println "\n>>>> Running integs with clojure" version)
      (apply run-tests namespaces)
      (println "\n<<<< Finished integs with clojure" version "\n")
      (finally (remove-clojure-jars)))))

(defn from-property []
  "Gets the namespace to test from the system property 'ns'"
  (if-let [value (read-string (System/getProperty "ns"))]
    (if (re-find #"^immutant\.integs\." (name value))
      (list value)
      (list (symbol (str "immutant.integs." (name value)))))))

(let [integs (io/file (.getParentFile (io/file *file*)) "integs")
      namespaces (or (from-property) (find-namespaces-in-dir integs))]
  (println "Testing namespaces:" namespaces)
  (apply require namespaces)
  (when-not *compile-files*
    (let [results (atom [])]
      (let [report-orig report]
        (binding [fntest.jboss/home "./target/integ-dist/jboss"
                  fntest.jboss/descriptor-root "target/.descriptors"
                  report (fn [x] (report-orig x)
                           (swap! results conj (:type x)))]
          (println "\n>>>> Testing against" clojure-versions "\n")
          (with-jboss #(for-each-version namespaces))))
      (shutdown-agents)
      (System/exit (if (empty? (filter #{:fail :error} @results)) 0 -1)))))

