(defproject
  basic-ring "1.0.0-SNAPSHOT" 
  :description "FIXME: write description" 
  :dependencies [[org.clojure/clojure "1.5.1"]
                 ;; this will trigger a TLD scanner error if the jar mounts aren't under the app root (IMMUTANT-190)
                 [org.mortbay.jetty/jsp-2.1 "6.1.14"]
                 [org.clojure/clojurescript "0.0-1586"]
                 [clj-http "0.5.5"]
                 [ritz/ritz-nrepl-middleware "0.7.0"]]
  :java-source-paths ["src/java/"]
  :immutant {:ham :biscuit}
  :repl-options {:nrepl-middleware [ritz.nrepl.middleware.doc/wrap-doc]})
