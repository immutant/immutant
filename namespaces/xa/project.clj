(defproject org.immutant/immutant-xa "1.0.3-SNAPSHOT"
  :description "The Immutant xa module."
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common _]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-xa-module :immutant]]}}
  :src-jar "../../modules/xa/target/immutant-xa-module-${version}.jar")
