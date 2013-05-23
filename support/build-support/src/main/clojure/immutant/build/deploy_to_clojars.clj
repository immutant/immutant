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

(ns immutant.build.deploy-to-clojars
  (:require [clojure.java.io :as io]
            [leiningen.core.project :as project]
            [leiningen.core.user :as user]
            [leiningen.deploy :as deploy]
            [cemerick.pomegranate.aether :as aether])
  (:gen-class))

(def key-id "BFC757F9")

(defn artifacts [dir project]
  (let [artifacts {[:extension "pom"] (.getAbsolutePath
                                       (io/file dir "pom.xml"))
                   [:extension "jar"] (.getAbsolutePath
                                       (io/file dir
                                                (format "%s-%s.jar"
                                                        (:name project)
                                                        (:version project))))}]
    ;; TODO: use the immutant cert
    (reduce merge artifacts
            (map #(deploy/signature-for-artifact % {:gpg-key key-id})
                 artifacts))))

(defn deploy [project artifacts]
  (println "Publishing artifacts for" (:name project))
  (aether/deploy
   :coordinates [(symbol (:group project) (:name project))
                 project]
   :artifact-map artifacts
   :transfer-listener :stdout
   :repository (deploy/repo-for project "clojars")))

(defn process-artifacts [base-dir]
  (println "Looking for artifacts in" base-dir)
  (for [dir (file-seq (io/file base-dir))
        :let [project-file (io/file dir "project.clj")]
        :when (.exists project-file)]
    (let [project (project/read (.getAbsolutePath project-file))]
      (deploy project (artifacts dir project)))))

(defn -main []
  (println "Pushing artifacts to clojars...")
  (if-let [dir (System/getProperty "dir")]
    (process-artifacts dir)
    (println "No dir provided - use -Ddir=/path/to/artifacts"))
  (shutdown-agents))
