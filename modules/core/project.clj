(defproject org.immutant/immutant-core-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common-module :immutant]
                 [org.immutant/immutant-bootstrap-module :immutant]
                 [org.projectodd/polyglot-core :polyglot]
                 [org.projectodd.shimdandy/shimdandy-api "1.0.1"]
                 [org.jboss.as/jboss-as-jmx :jbossas]]
  :profiles {:dev {:dependencies [[lein-modules "0.1.0-SNAPSHOT" :scope "test"]]}})

