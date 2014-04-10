(defproject org.immutant/immutant-support-parent "1.1.1-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.2"]]
  :packaging "pom"
  :modules {:dirs ["as-test-support" "clojure-test-support"]})
