(defproject org.immutant/immutant-jobs "1.1.1-SNAPSHOT"
  :description "The Immutant jobs module."
  :plugins [[lein-modules "0.2.0"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common _]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-jobs-module :immutant]]}}
  :src-jar "../../modules/jobs/target/immutant-jobs-module-${version}.jar")
