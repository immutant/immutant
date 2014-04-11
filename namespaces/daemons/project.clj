(defproject org.immutant/immutant-daemons "1.1.2-SNAPSHOT"
  :description "The Immutant daemons module."
  :plugins [[lein-modules "0.2.2"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common :immutant]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-daemons-module :immutant]]}}
  :src-jar "../../modules/daemons/target/immutant-daemons-module-${version}.jar")
