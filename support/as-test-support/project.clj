(defproject org.immutant/immutant-as-test-support "1.0.3-SNAPSHOT"
  :description "Immutant JBoss AS Integration Testing Support"
  :parent [org.immutant/immutant-support-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.0"]]
  :dependencies [[org.projectodd/polyglot-as-test-support _]])
