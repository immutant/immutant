(defproject org.immutant/immutant-web "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-web-module _]
                 [javax.servlet/javax.servlet-api "3.0.1"]
                 [org.jboss.web/jbossweb "7.2.0.Final"]]
  :modules {:parent "../project.clj"})
