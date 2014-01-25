(defproject org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT"
  :description "Parent for all modules"
  ;; :parent [org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  ;; :relative-path "../pom.xml"]
  :modules  {:inherited {:dependencies [[org.clojure/clojure "1.5.1"]
                                        [org.jboss.as/jboss-as-server "7.2.x.slim.incremental.12"]
                                        [midje "1.6.0" :scope "test"]
                                        [org.immutant/immutant-clojure-test-support "1.0.3-SNAPSHOT" :scope "test"]
                                        [org.immutant/immutant-as-test-support "1.0.3-SNAPSHOT" :scope "test"]]
                         :repositories [["project:odd upstream" "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                         :source-paths ^:replace ["src/main/clojure"]
                         :test-paths ^:replace ["src/test/clojure"]
                         :resource-paths ["src/module/resources"]
                         :java-source-paths ^:replace ["src/main/java"]
                         :jar-exclusions [#"\.java$"]
                         :aliases {"all" ["do" "clean," "test," "jar"]}}})
