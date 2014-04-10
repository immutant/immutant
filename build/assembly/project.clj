(defproject org.immutant/immutant-build-assembly "1.1.1-SNAPSHOT"
  :parent [org.immutant/immutant-build _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.2.2"]]
  :packaging "pom"

  :profiles {:provided
             {:dependencies [[org.jboss.as/jboss-as-dist _ :extension "zip"]
                             [org.immutant/immutant _]]}}

  :modules {:dirs ^:replace []}

  :aliases {"install" ["do" "assemble," "install"]})
