(defproject org.immutant/immutant-namespaces-parent "1.1.2-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.4"]]
  :packaging "pom"
  :profiles {:dev {:hooks [immutant.build.plugin.namespaces/hooks]}})
