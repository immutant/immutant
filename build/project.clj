(defproject org.immutant/immutant-build "1.1.1-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.0"]]
  :packaging "pom"

  :modules {:dirs ["assembly"]}

  :profiles {:dist {:modules {:dirs ["dist"]}}})
