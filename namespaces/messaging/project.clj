(defproject org.immutant/immutant-messaging "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-messaging-module _]
                 [org.hornetq/hornetq-jms-client "2.3.1.Final"]
                 [org.hornetq/hornetq-core-client "2.3.1.Final"]
                 [io.netty/netty "3.6.2.Final"]]
  :modules {:parent "../project.clj"})
