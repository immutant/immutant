(defproject org.immutant/immutant-cache "1.1.2-SNAPSHOT"
  :description "The Immutant cache module."
  :plugins [[lein-modules "0.2.2"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.clojure/core.memoize "0.5.5"]
                 [org.immutant/immutant-common _]
                 [org.infinispan/infinispan-core _]
                 [org.jboss.logging/jboss-logging "3.1.2.GA"]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]
                 [org.projectodd/polyglot-cache _]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-cache-module :immutant]]}}
  :src-jar "../../modules/cache/target/immutant-cache-module-${version}.jar")
