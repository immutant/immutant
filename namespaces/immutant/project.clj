(defproject org.immutant/immutant "1.0.3-SNAPSHOT"
  :description "An aggregate lib that pulls in all of the public Immutant libs."
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]
            [org.immutant/build-plugin "0.1.0-SNAPSHOT"]]
  :modules {:parent "../project.clj"}
  :dependencies [[org.immutant/immutant-cache :immutant]
                 [org.immutant/immutant-daemons :immutant]
                 [org.immutant/immutant-jobs :immutant]
                 [org.immutant/immutant-messaging :immutant]
                 [org.immutant/immutant-web :immutant]
                 [org.immutant/immutant-xa :immutant]])
