(defproject org.immutant/immutant-namespaces-parent "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.0"]]
  :packaging "pom"
  :profiles {:dev {:hooks [immutant.build.plugin.namespaces/hooks]}})
