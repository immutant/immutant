(ns immutant.build.plugin.assembly
  (:use [leiningen.jruby :only (jruby)])
  (:require [robert.hooke]
            [leiningen.javac]
            [clojure.java.io :as io]))

(defn generate-build-info [f & args]
  (let [result (apply f args)
        [project] args
        script (io/file (:root project) "bin/generate-build-info.rb")]
    (if (.exists script)
      (jruby project (str script)))
    result))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.javac/javac #'generate-build-info) )
