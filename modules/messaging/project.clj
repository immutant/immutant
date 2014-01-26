(defproject org.immutant/immutant-messaging-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common :immutant]
                 [org.immutant/immutant-xa-module :immutant]
                 [org.immutant/immutant-core-module :immutant]
                 [org.projectodd/polyglot-core :polyglot]
                 [org.projectodd/polyglot-messaging :polyglot]
                 [org.jboss.spec.javax.jms/jboss-jms-api_1.1_spec "1.0.1.Final"]
                 [org.jboss.as/jboss-as-messaging :jbossas]
                 [org.jboss.as/jboss-as-jmx :jbossas]])

