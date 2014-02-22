(defproject org.immutant/immutant-dist "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-build _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :packaging "pom"

  :modules {:dirs ^:replace []}  )
