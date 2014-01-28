(defproject org.immutant/immutant-core-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-common-module _]
                 [org.immutant/immutant-bootstrap-module _]
                 [org.projectodd/polyglot-core _]
                 [org.projectodd.shimdandy/shimdandy-api "1.0.1"]
                 [org.jboss.as/jboss-as-jmx _]])

