(defproject smoketest "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.immutant/immutant ~(System/getProperty "version")]]
  :local-repo "target/.m2")
