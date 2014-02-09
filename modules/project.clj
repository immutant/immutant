 (defproject org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT"
  :description "Parent for all modules"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :packaging "pom"
  
  :profiles  {:dev {:dependencies [[org.immutant/immutant-clojure-test-support _]
                                   [org.immutant/immutant-as-test-support _]]}})
