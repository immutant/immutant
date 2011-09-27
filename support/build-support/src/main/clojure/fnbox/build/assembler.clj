(ns fnbox.build.assembler
  (:require [clojure.java.io :as io])
  (:use [fnbox.build.tools])
  (:gen-class))

(defn prepare []
  (with-message (str "Creating " fnbox-dir)
    (io/make-parents fnbox-dir)))

(defn lay-down-jboss []
  (when-not (.exists jboss-dir)
    (with-message "Laying down jboss"
      (unzip jboss-zip-file fnbox-dir))
    (let [unzipped (str "jboss-as-" (:jboss versions))]
      (with-message (str "Moving " unzipped " to jboss")
        (.renameTo (io/file fnbox-dir unzipped) jboss-dir)))))

(defn install-modules []
  (with-message "Installing modules"
    (doall (map install-module 
                (sort-by #(if (re-matches #"-core" (.getName %)) -1 1)
                         (vals fnbox-modules))))))

(defn transform-configs []
  (doall (map transform-config
              ["standalone/configuration/standalone-preview.xml"
               "standalone/configuration/standalone-preview-ha.xml"
               "domain/configuration/domain.xml"
               "domain/configuration/domain-preview.xml"])))

(defn create-standalone-xml []
  (io/copy (io/file jboss-dir "standalone/configuration/fnbox/standalone-preview.xml")
           (io/file jboss-dir "standalone/configuration/standalone.xml")))

(defn assemble [assembly-dir]
  (init assembly-dir)
  (prepare)
  (println (str "(fn box)..... " (:fnbox versions)))
  (println (str "JBoss AS..... " (:jboss versions)))
  (lay-down-jboss)
  (install-modules)
  (transform-configs)
  (create-standalone-xml))

(defn -main [assembly-path]
  (println "Assembling (fn box)...")
  (assemble (io/file assembly-path)))
