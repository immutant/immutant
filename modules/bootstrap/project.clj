(defproject org.immutant/immutant-bootstrap-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-common-module _]
                 [leiningen-core _]
                 [org.immutant/immutant-dependency-exclusions "0.1.0"]])

