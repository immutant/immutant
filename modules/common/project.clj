(defproject org.immutant/immutant-common-module "1.0.3-SNAPSHOT"
  :description "Stuff needed by other modules"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-common _]])
