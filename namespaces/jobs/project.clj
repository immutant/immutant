(defproject org.immutant/immutant-jobs "1.0.3-SNAPSHOT"
  :description "The Immutant jobs module."
  :plugins [[lein-modules "0.1.0"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common _]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-jobs-module :immutant]]}}
  :src-jar "../../modules/jobs/target/immutant-jobs-module-${version}.jar")
