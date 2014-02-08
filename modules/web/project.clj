(defproject org.immutant/immutant-web-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :dependencies [[ring/ring-servlet _]
                 [ring/ring-devel _]]
  :profiles {:provided
             {:dependencies [[org.immutant/immutant-core-module _]
                             [org.immutant/immutant-common-module _]
                             [org.projectodd/polyglot-web _]
                             [org.tcrawley/dynapath "0.2.3"]
                             [org.jboss.as/jboss-as-web _]]}
             :dev
             {:dependencies [[org.immutant/immutant-bootstrap-module _]]}})

