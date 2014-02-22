(ns leiningen.immutant
  (:use [leiningen.jruby :only (jruby)])
  (:require [robert.hooke]
            [leiningen.pom]
            [leiningen.jar]
            [leiningen.javac]
            [leiningen.core.main :as main]
            [leiningen.core.project :as prj]
            [leiningen.core.classpath :as cp]
            [clojure.java.io :as io]))

(defn copy-jar [f & args]
  "Only because our 'namespaces' jars are really our 'modules' jars"
  (let [[project] args]
    (if-let [src (and (= 2 (count args)) (:src-jar project))]
      (let [src (io/file (:root project)
                  (.replace src "${version}" (:version project)))
            dest (io/file (:target-path project)
                   (format "%s-%s.jar" (:name project) (:version project)))]
        (.mkdirs (.getParentFile dest))
        (io/copy src dest)
        (main/info "Copied" (str dest))
        {[:extension "jar"] (str dest)})
      (apply f args))))

(defn put-pom-in-target [f & args]
  (let [[project pom] args]
    (if (= pom "pom.xml")
      (f project "target/pom.xml")
      (apply f args))))

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

(defn generate-build-info [f & args]
  (let [result (apply f args)
        [project] args
        script (io/file (:root project) "bin/generate-build-info.rb")]
    (if (.exists script)
      (jruby project (str script)))
    result))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.pom/pom #'put-pom-in-target)
  (robert.hooke/add-hook #'leiningen.jar/jar #'copy-jar)
  (robert.hooke/add-hook #'leiningen.jar/jar #'prepare-module-after-jar))

(defn versions []
  (robert.hooke/add-hook #'leiningen.javac/javac #'generate-build-info))
