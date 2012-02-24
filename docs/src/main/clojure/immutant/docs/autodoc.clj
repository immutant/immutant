(ns immutant.docs.autodoc
  (:require [clojure.java.io      :as io]
            [clojure.walk         :as walk]
            [autodoc.params       :as params]
            [autodoc.build-html   :as html]
            [autodoc.copy-statics :as static]
            [autodoc.autodoc      :as admain])
  (:gen-class))

(def ^{:dynamic true} *root-path*)

(defn strip-prefix [file-name]
  (.replace (.getCanonicalPath (io/file file-name))
            (.getCanonicalPath (io/file *root-path*)) "."))

(defn fix-paths [data]
  (walk/postwalk #(if-let [f (:file %)]
                    (assoc % :file (strip-prefix f))
                    %) data))

(defn load-indexes [modules-dir]
  (fix-paths
   (reduce (fn [acc file]
             (println "Processing" (.getAbsolutePath file))
             (into acc (read-string (slurp file))))
           '()
           (filter #(and (.exists %)
                         (.endsWith (.getAbsolutePath %) "autodoc/raw-index.clj"))
                   (file-seq modules-dir)))))

(def params
  {:root (.getAbsolutePath (io/file ".."))
   :load-classpath [#""] 
   :output-path "target/html/apidoc"
   :param-dir "src/main/resources"
   :web-src-dir "https://github.com/immutant/immutant/blob/"
   :name "Immutant Clojure API"
   :description ""
   :copyright "Copyright 2011-2012 Red Hat, Inc."})

(defn -main [root-path]
  (println "Generating autodocs...")
  (params/merge-params params)
  (admain/make-doc-dir)
  (static/copy-statics)
  (binding [*root-path* root-path]
    (html/make-all-pages {:first? true} nil (load-indexes (io/file "../modules"))))
  (shutdown-agents))
