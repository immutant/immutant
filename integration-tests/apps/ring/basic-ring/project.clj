(defproject
  basic-ring "1.0.0-SNAPSHOT" 
  :description "FIXME: write description" 
  :dependencies [[org.clojure/clojure "1.3.0"]
                 ;; this will trigger a TLD scanner error if the jar mounts aren't under the app root (IMMUTANT-190)
                 [org.mortbay.jetty/jsp-2.1 "6.1.14"]]
  :java-source-paths ["src/java/"]
  :compile-path "classes"
  :immutant {:ham :biscuit})
