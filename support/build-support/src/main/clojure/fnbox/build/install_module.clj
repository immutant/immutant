(ns fnbox.build.install-module
  (:require [clojure.java.io :as io])
  (:use [fnbox.build.tools])
  (:gen-class))

(defn -main [assembly-path module-path]
  (let [module-dir (.getCanonicalFile (io/file module-path))]
    (println (str "Installing module from " module-dir))
    (init (io/file assembly-path))
    (install-module module-dir)))
