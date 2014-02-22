(defproject org.immutant/immutant-build-assembly "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-build _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :packaging "pom"

  :profiles {:provided
             {:dependencies [[org.jboss.as/jboss-as-dist _ :extension "zip"]
                             [org.immutant/immutant :immutant]]}}

  :modules {:dirs ^:replace []}

  :aliases {"install" "assemble", "all" ["do" "clean," "assemble"]})
