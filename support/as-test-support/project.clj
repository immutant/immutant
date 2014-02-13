(defproject org.immutant/immutant-as-test-support "1.0.3-SNAPSHOT"
  :description "Immutant JBoss AS Integration Testing Support"
  :parent [org.immutant/immutant-support-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :dependencies [[org.projectodd/polyglot-as-test-support _]])
