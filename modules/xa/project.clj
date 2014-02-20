(defproject org.immutant/immutant-xa-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :profiles {:provided
             {:dependencies [[org.projectodd/polyglot-core _]
                             [org.projectodd/polyglot-xa _]
                             [org.jboss.as/jboss-as-jmx _]
                             [org.immutant/immutant-core-module _]
                             [org.immutant/immutant-common _]]}}

  :dependencies [[org.jboss.spec.javax.transaction/jboss-transaction-api_1.1_spec "1.0.1.Final"]])

