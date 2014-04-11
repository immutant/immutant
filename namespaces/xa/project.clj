(defproject org.immutant/immutant-xa "1.1.2-SNAPSHOT"
  :description "The Immutant xa module."
  :plugins [[lein-modules "0.2.2"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-common _]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-xa-module :immutant]]}}
  :src-jar "../../modules/xa/target/immutant-xa-module-${version}.jar")
