(defproject org.immutant/immutant-xa-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.projectodd/polyglot-core :polyglot]
                 [org.projectodd/polyglot-xa :polyglot]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]
                 [org.jboss.as/jboss-as-jmx :jbossas]
                 [org.immutant/immutant-core-module :immutant]
                 [org.immutant/immutant-common :immutant]])

