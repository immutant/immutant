(defproject org.immutant/immutant-cache-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-core-module :immutant]
                 [org.immutant/immutant-common :immutant]
                 [org.projectodd/polyglot-core :polyglot]
                 [org.projectodd/polyglot-cache :polyglot]
                 [org.clojure/core.memoize "0.5.5"]
                 [org.infinispan/infinispan-core :infinispan]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]])

