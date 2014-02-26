(ns immutant.build.plugin.integs
  (:use [leiningen.jruby :only (jruby)]
        [leiningen.resource :only (resource)]
        [environ.core :only (env)])
  (:require [robert.hooke]
            [leiningen.test]
            [clojure.java.io :as io]))

(defn prep-for-test [f & args]
  (let [[project] args
        script (io/file (:root project) "bin/setup-integ-dist.rb")]
    (when (.exists script)
      (resource project)
      (jruby project (str script) (env :assembly-dir) (env :integ-dist-dir)))
    (apply f args)))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.test/test #'prep-for-test) )
