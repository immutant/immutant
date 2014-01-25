(defproject org.immutant/immutant-cache-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT" :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-core-module "1.0.3-SNAPSHOT"]
                 [org.immutant/immutant-common "1.0.3-SNAPSHOT"]
                 [org.projectodd/polyglot-core "1.x.incremental.61"]
                 [org.projectodd/polyglot-cache "1.x.incremental.61"]
                 [org.clojure/core.memoize "0.5.5"]
                 [org.infinispan/infinispan-core "6.0.0.Final"]
                 [org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]]
  :profiles {:dev {:resource-paths ["src/test/resources"]}})

