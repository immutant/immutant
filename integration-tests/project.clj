(defproject org.immutant/immutant-integration-tests "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [lein-resource "0.3.3"]
            [lein-environ "0.4.0"]]
  :dependencies [[org.immutant/immutant-build-assembly :immutant]
                 [org.immutant/fntest "0.5.2"]
                 [clj-http "0.7.2"]
                 [leiningen-core _]
                 [org.immutant/deploy-tools "0.12.0"]]

  :aliases {"all" ["check"]}
  :profiles {:dev
             {:dependencies [[org.immutant/immutant-messaging :immutant]
                             [org.clojars.tcrawley/java.jmx "0.3.0"]
                             [org.jboss.remotingjmx/remoting-jmx "1.1.0.Final"]
                             [environ "0.4.0"]]}
             :cluster {:env {:modes "offset,domain"}}
             :integ   {:aliases {"all" ^:replace ["do" "clean," "test"]}}}

  :resource {:resource-paths ["apps"]
             :target-path "target/apps"
             :skip-stencil [ #".*"]}

  :env {:assembly-dir "../build/assembly/target/stage/immutant"
        :integ-dist-dir "target/integ-dist"
        :test-ns-path "src/test/clojure"
        :databases "h2"
        :versions  "1.6.0-alpha2"
        :modes     "offset"}

  :hooks [immutant.build.plugin.integs/hooks])
