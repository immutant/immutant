(ns leiningen.immutant
  (:require [robert.hooke]
            [leiningen.pom]
            [leiningen.jar]
            [leiningen.core.main :as main]
            [clojure.java.io :as io]))

(defn copy-jar [f & args]
  (let [[project main] args]
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

(defn hooks []
  (robert.hooke/add-hook #'leiningen.pom/pom #'put-pom-in-target)
  (robert.hooke/add-hook #'leiningen.jar/jar #'copy-jar))
