(ns immutant.build.plugin.pom
  (:require [robert.hooke]
            [leiningen.pom]))

(defn put-pom-in-target [f & args]
  (let [[project pom] args]
    (if (= pom "pom.xml")
      (f project "target/pom.xml")
      (apply f args))))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.pom/pom #'put-pom-in-target))
