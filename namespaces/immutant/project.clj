(defproject org.immutant/immutant "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.immutant/immutant-messaging _]
                 [org.immutant/immutant-cache _]
                 [org.immutant/immutant-daemons _]
                 [org.immutant/immutant-jobs _]
                 [org.immutant/immutant-web _]
                 [org.immutant/immutant-xa _]]
  :modules {:parent "../project.clj"})
