(defproject org.immutant/immutant-bootstrap-module "1.1.2-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.2"]]
  :profiles {:provided
             {:dependencies [[org.immutant/immutant-common-module _]]}}
  :dependencies [[org.clojure/clojure _]
                 [leiningen-core _]
                 [org.immutant/immutant-dependency-exclusions _]
                 [org.tcrawley/dynapath _]
                 [s3-wagon-private "1.1.2"]])

