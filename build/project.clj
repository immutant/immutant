(defproject org.immutant/immutant-build "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :packaging "pom"

  :modules {:dirs ["assembly"]}

  :profiles {:dist {:modules {:dirs ["dist"]}}})
