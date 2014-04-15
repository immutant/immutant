(defproject org.immutant/immutant-dist "1.1.2-SNAPSHOT"
  :parent [org.immutant/immutant-build _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.4"]]
  :packaging "pom"

  :modules {:dirs ^:replace []}  )
