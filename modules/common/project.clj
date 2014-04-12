(defproject org.immutant/immutant-common-module "1.1.2-SNAPSHOT"
  :description "Stuff needed by other modules"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.3"]]
  :dependencies [[org.immutant/immutant-common _]])
