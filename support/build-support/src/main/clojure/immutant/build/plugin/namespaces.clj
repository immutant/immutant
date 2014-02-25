(ns immutant.build.plugin.namespaces
  (:require [robert.hooke]
            [leiningen.jar]
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

(defn hooks []
  (robert.hooke/add-hook #'leiningen.jar/jar #'copy-jar))
