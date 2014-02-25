(ns immutant.build.plugin.modules
  (:require [robert.hooke]
            [leiningen.jar]
            [leiningen.core.main :as main]
            [leiningen.core.project :as prj]
            [leiningen.core.classpath :as cp]
            [clojure.java.io :as io]))

(defn- copy-sans-version [dir file]
  (let [name (apply str (rest (re-find #"(.*)-\d.*(\..*)$" (.getName file))))
        dest (io/file dir (if (empty? name) (.getName file) name))]
    (io/copy file dest)))

(defn prepare-module-after-jar [f & args]
  (let [result (apply f args)
        [project] args
        module (io/file (:root project) "src/module/resources/module.xml")]
    (if (.exists module)
      (let [dir (io/file (:target-path project) (str (:name project) "-module"))]
        (.mkdirs dir)
        (spit (io/file dir "module.xml")
          (.replace (slurp module) "${project.artifactId}" (:name project)))
        (copy-sans-version dir (io/file (result [:extension "jar"])))
        (doseq [dep (cp/resolve-dependencies :dependencies (prj/unmerge-profiles project [:default]))]
          (copy-sans-version dir dep))))
    result))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.jar/jar #'prepare-module-after-jar))
