(defproject org.immutant/immutant-cache "1.0.3-SNAPSHOT"
  :description "The Immutant cache module."
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.clojure/core.memoize "0.5.5"]
                 [org.immutant/immutant-common _]
                 [org.infinispan/infinispan-core _]
                 [org.jboss.logging/jboss-logging "3.1.2.GA"]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]
                 [org.projectodd/polyglot-cache _]
                 [org.immutant/immutant-cache-module :immutant :scope "provided"]]
  :src-jar "../../modules/cache/target/immutant-cache-module-${version}.jar")
