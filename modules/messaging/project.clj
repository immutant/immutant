(defproject org.immutant/immutant-messaging-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT" :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common "1.0.3-SNAPSHOT"]
                 [org.immutant/immutant-xa-module "1.0.3-SNAPSHOT"]
                 [org.immutant/immutant-core-module "1.0.3-SNAPSHOT"]
                 [org.projectodd/polyglot-core "1.x.incremental.61"]
                 [org.projectodd/polyglot-messaging "1.x.incremental.61"]
                 [org.jboss.spec.javax.jms/jboss-jms-api_1.1_spec "1.0.1.Final"]
                 [org.jboss.as/jboss-as-messaging "7.2.x.slim.incremental.12"]
                 [org.jboss.as/jboss-as-jmx "7.2.x.slim.incremental.12"]])

