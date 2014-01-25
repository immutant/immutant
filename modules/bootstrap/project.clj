(defproject org.immutant/immutant-bootstrap-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT" :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common-module "1.0.3-SNAPSHOT"]
                 [leiningen-core "2.3.4"]
                 [org.immutant/immutant-dependency-exclusions "0.1.0"]]
  :profiles {:test {:resource-paths ["src/test/resources"]}
             :dev {:dependencies [[lein-modules "0.1.0-SNAPSHOT" :scope "test"]]}})

