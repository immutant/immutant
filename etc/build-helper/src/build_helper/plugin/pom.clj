(ns build-helper.plugin.pom
  (:require [robert.hooke]
            [leiningen.pom]
            [leiningen.test]))

(defn put-pom-in-target [f & args]
  (let [[project pom] args]
    (if (= pom "pom.xml")
      (f project "target/pom.xml")
      (apply f args))))

(defn skip-tests-for-pom-packages [f & args]
  (let [[project] args]
    (if-not (= "pom" (:packaging project))
      (apply f args))))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.pom/pom #'put-pom-in-target)
  (robert.hooke/add-hook #'leiningen.test/test #'skip-tests-for-pom-packages))
