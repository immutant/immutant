(defproject org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT"
  :description "Parent for all modules"
  ;; :parent [org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  ;; :relative-path "../pom.xml"]
  :modules  {:inherited {:dependencies [[org.clojure/clojure :clojure]
                                        [org.jboss.as/jboss-as-server :jbossas]
                                        [midje "1.6.0" :scope "test"]
                                        [org.immutant/immutant-clojure-test-support :immutant :scope "test"]
                                        [org.immutant/immutant-as-test-support :immutant :scope "test"]]
                         :repositories [["project:odd upstream" "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                         :source-paths ^:replace ["src/main/clojure"]
                         :test-paths ^:replace ["src/test/clojure"]
                         :resource-paths ^:replace ["src/module/resources" "src/test/resources"]
                         :java-source-paths ^:replace ["src/main/java"]
                         :jar-exclusions [#"\.java$"]
                         :aliases {"all" ["do" "clean," "test," "jar"]}

                         ;; TODO: Have plugin put this in
                         ;; :without-profiles metadata? Otherwise, the
                         ;; pom and jar tasks unmerge [:default] and
                         ;; versionization doesn't occur
                         :plugins [[lein-modules "0.1.0-SNAPSHOT"]]

                         }
             :versions {:clojure     "1.5.1"
                        :leiningen   "2.3.4"
                        :immutant    "1.0.3-SNAPSHOT"
                        :ring        "1.2.1"
                        :jbossas     "7.2.x.slim.incremental.12"
                        :polyglot    "1.x.incremental.61"
                        :infinispan  "6.0.0.Final"}})
