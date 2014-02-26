(defproject org.immutant/immutant-core-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.0"]]
  :profiles {:provided
             {:dependencies [[org.projectodd/polyglot-core _]
                             [org.immutant/immutant-common-module _]
                             [org.immutant/immutant-bootstrap-module _]
                             [org.jboss.as/jboss-as-web _]
                             [org.jboss.as/jboss-as-jmx _]
                             [org.hornetq/hornetq-core-client _]]}}
  :dependencies [[org.projectodd.shimdandy/shimdandy-api _]
                 [org.projectodd.shimdandy/shimdandy-impl _]
                 [org.clojure/tools.nrepl "" :exclusions [org.clojure/clojure]]
                 [clj-stacktrace _ :exclusions [org.clojure/clojure]]
                 [clojure-complete _ :exclusions [org.clojure/clojure]]
                 [org.tcrawley/dynapath _]]
  :hooks [immutant.build.plugin.assembly/hooks])
