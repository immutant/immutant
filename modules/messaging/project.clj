(defproject org.immutant/immutant-messaging-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-common _]
                 [org.immutant/immutant-xa-module _]
                 [org.immutant/immutant-core-module _]
                 [org.projectodd/polyglot-core _]
                 [org.projectodd/polyglot-messaging _]
                 [org.jboss.spec.javax.jms/jboss-jms-api_1.1_spec "1.0.1.Final"]
                 [org.jboss.as/jboss-as-messaging _]
                 [org.jboss.as/jboss-as-jmx _]])

