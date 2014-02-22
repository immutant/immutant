(defproject org.immutant/immutant-build-support "1.0.3-SNAPSHOT"
  :dependencies [[org.clojure/data.zip "0.1.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-io "2.0.1"]
                 [clj-glob "1.0.0"]
                 [org.clojars.tcrawley/codox.core "0.6.3"]
                 [digest "1.4.0"]
                 [cheshire "5.2.0"]
                 [leiningen-core "2.3.4"]
                 [org.jruby/jruby-complete "1.6.7"]
                 [lancet "1.0.1"]]

  :source-paths ["src/main/clojure"]
  
  ;; Boom! We're a plugin!
  :eval-in-leiningen true)
