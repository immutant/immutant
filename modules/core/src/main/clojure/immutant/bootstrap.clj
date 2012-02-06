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

(ns immutant.bootstrap
  "Functions used in app bootstrapping."
  (:require [clojure.java.io          :as io]
            [clojure.walk             :as walk]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project   :as project])
  (:import [java.io   FilenameFilter]
           [java.util ArrayList]))

(defn read-descriptor
  "Reads a deployment descriptor and returns the resulting hash."
  [file]
  (walk/stringify-keys (load-file (.getAbsolutePath file))))

(defn read-project
  "Reads a leiningen project.clj file in the given root dir."
  [app-root]
  (let [project-file (io/file app-root "project.clj")]
    (when (.exists project-file)
      (project/read (.getAbsolutePath project-file)))))

(defn resolve-dependencies [project]
  (classpath/resolve-dependencies project))

(defn bundled-jars [app-root]
  (let [lib-dir (io/file app-root "lib")]
    (if (.isDirectory lib-dir)
      (.listFiles lib-dir (proxy [FilenameFilter] []
                            (accept [_ file-name]
                              (.endsWith file-name ".jar")))))))

;; TODO: add support for a file:// repo that is used with a archived app
(defn get-dependencies [app-root]
  (let [bundled (bundled-jars app-root)
        bundled-jar-names (map #(.getName %) bundled)]
    (concat
     bundled
     (filter #(not (some #{(.getName %)} bundled-jar-names))
             (resolve-dependencies (read-project app-root))))))
