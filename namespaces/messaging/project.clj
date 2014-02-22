(defproject org.immutant/immutant-messaging "1.0.3-SNAPSHOT"
  :description "The Immutant messaging module."
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[io.netty/netty "3.6.2.Final"]
                 [org.hornetq/hornetq-core-client :hornetq]
                 [org.hornetq/hornetq-jms-client :hornetq]
                 [org.immutant/immutant-common _]
                 [org.immutant/immutant-xa :immutant]
                 [org.jboss.spec.javax.jms/jboss-jms-api_1.1_spec "1.0.1.Final"]]
  :src-jar "../../modules/messaging/target/immutant-messaging-module-${version}.jar")
