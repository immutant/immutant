(defproject org.immutant/immutant-daemons "1.0.3-SNAPSHOT"
  :description "The Immutant daemons module."
  :plugins [[lein-modules "0.1.0"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common :immutant]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-daemons-module :immutant]]}}
  :src-jar "../../modules/daemons/target/immutant-daemons-module-${version}.jar")
