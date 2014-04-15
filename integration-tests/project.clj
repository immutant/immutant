(defproject org.immutant/immutant-integration-tests "1.1.2-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.4"]
            [lein-resource "0.3.4"]
            [lein-environ "0.4.0"]]
  :dependencies [[org.immutant/immutant-build-assembly :immutant :extension "pom"]
                 [org.immutant/fntest _]
                 [clj-http "0.7.2"]
                 [leiningen-core _]
                 [org.immutant/deploy-tools _]]

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
             :update true
             :skip-stencil [ #".*"]}

  :env {:assembly-dir "../build/assembly/target/stage/immutant"
        :integ-dist-dir "target/integ-dist"
        :test-ns-path "src/test/clojure"
        :databases "h2"
        :versions  "1.6.0"
        :modes     "offset"}

  :hooks [immutant.build.plugin.integs/hooks])
