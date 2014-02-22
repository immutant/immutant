(ns leiningen.assemble
  (:require [clojure.java.io :as io]
            [leiningen.core.project :as prj]
            [immutant.build.assembler :as ass]))

(defn assemble [project]
  (let [prop "version.jbossas"
        deps (->> project :dependencies (map prj/dependency-map))
        dist (some #(when (= "jboss-as-dist" (:artifact-id %)) %) deps)]
    (if-not (System/getProperty prop)
      (System/setProperty prop (:version dist)))
    (ass/assemble (io/file (:root project)) :slim)))
