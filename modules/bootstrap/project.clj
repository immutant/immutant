(defproject org.immutant/immutant-bootstrap-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common-module :immutant]
                 [leiningen-core :leiningen]
                 [org.immutant/immutant-dependency-exclusions "0.1.0"]]
  :profiles {:dev {:dependencies [[lein-modules "0.1.0-SNAPSHOT" :scope "test"]]}})

