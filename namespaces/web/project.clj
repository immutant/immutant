(defproject org.immutant/immutant-web "1.1.1-SNAPSHOT"
  :description "The Immutant web module."
  :plugins [[lein-modules "0.2.2"]]
  :modules {:parent "../project.clj"}
  :dependencies [[javax.servlet/javax.servlet-api "3.0.1"]
                 [org.immutant/immutant-common _]
                 [org.tcrawley/dynapath "0.2.3"]
                 [ring/ring-devel _]
                 [ring/ring-servlet _]]

  :profiles {:provided {:dependencies [[org.immutant/immutant-web-module :immutant]]}}
  :src-jar "../../modules/web/target/immutant-web-module-${version}.jar")
