(ns immutant.build.install-artifacts
  (:require [leiningen.core.project :as project]
            [leiningen.pom :as pom]
            [cemerick.pomegranate.aether :as aether]
            [clojure.java.io :as io])
  (:gen-class))

(defn install [dir]
  (let [project (project/read (.getAbsolutePath (io/file dir "project.clj")))
        jar-file (io/file dir (str (:name project) \- (:version project) ".jar"))]
    (aether/install :coordinates [(symbol (:group project) (:name project))
                                  (:version project)]
                    :jar-file jar-file
                    :pom-file (pom/pom project)
                    :local-repo (:local-repo project))))

(defn -main [basedir]
  (doseq [f (.listFiles (io/file basedir))]
    (when (and (.startsWith (.getName f) "immutant") (.isDirectory f))
      (println "==> Installing from" (.getName f))
      (install f))))
