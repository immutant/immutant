(defproject org.immutant/immutant-web-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :dependencies [[ring/ring-servlet _ :exclusions [org.clojure/clojure]]
                 [ring/ring-devel _ :exclusions [org.clojure/clojure org.clojure/java.classpath]]
                 [org.clojure/tools.namespace "0.1.3"]]
  :profiles {:provided
             {:dependencies [[org.immutant/immutant-core-module _]
                             [org.immutant/immutant-common-module _]
                             [org.projectodd/polyglot-core _]
                             [org.projectodd/polyglot-web _]
                             [org.tcrawley/dynapath _]
                             [org.jboss.as/jboss-as-web _]]}
             :dev
             {:dependencies [[org.immutant/immutant-bootstrap-module _]]}})

