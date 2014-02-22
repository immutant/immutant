(defproject org.immutant/immutant-bootstrap-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :profiles {:provided
             {:dependencies [[org.immutant/immutant-common-module _]]}}
  :dependencies [[leiningen-core _]
                 [org.immutant/immutant-dependency-exclusions "0.1.0"]
                 [org.tcrawley/dynapath _]
                 [s3-wagon-private "1.1.2"]])

