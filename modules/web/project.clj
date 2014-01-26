(defproject org.immutant/immutant-web-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-core-module :immutant]
                 [org.immutant/immutant-common-module :immutant]
                 [org.projectodd/polyglot-core :polyglot]
                 [org.projectodd/polyglot-web :polyglot]
                 [org.tcrawley/dynapath "0.2.3"]
                 [ring/ring-servlet :ring]
                 [ring/ring-devel :ring]
                 [org.jboss.as/jboss-as-web :jbossas]]
  :profiles {:dev {:dependencies [[org.immutant/immutant-bootstrap-module :immutant :scope "test"]]}})

