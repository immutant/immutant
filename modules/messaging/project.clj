(defproject org.immutant/immutant-messaging-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.0"]]
  :profiles {:provided
             {:dependencies [[org.immutant/immutant-common _]
                             [org.projectodd/polyglot-core _]
                             [org.projectodd/polyglot-messaging _]
                             [org.immutant/immutant-core-module _]
                             [org.jboss.as/jboss-as-messaging _]
                             [org.jboss.as/jboss-as-jmx _]
                             [org.immutant/immutant-xa-module _]
                             [org.jboss.spec.javax.jms/jboss-jms-api_1.1_spec "1.0.1.Final"]]}})

