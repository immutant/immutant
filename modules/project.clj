(defproject org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT"
  :description "Parent for all modules"
  ;; :parent [org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  ;; :relative-path "../pom.xml"]
  :profiles {:inherited {:dependencies [[org.jboss.as/jboss-as-server "7.2.x.slim.incremental.12"]]
                         :repositories [["project:odd upstream" "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                         :source-paths ^:replace ["src/main/clojure"]
                         :test-paths ^:replace ["src/test/clojure"]
                         :resource-paths ["src/module/resources"]
                         :java-source-paths ^:replace ["src/main/java"]
                         :jar-exclusions [#"\.java$"]}})
