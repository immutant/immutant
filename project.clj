 (defproject org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  :description "Parent for all that is Immutant"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :packaging "pom"
  :modules  {:inherited {:dependencies [[org.clojure/clojure _]]
                         :repositories [["project:odd upstream" "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                         :source-paths ^:replace ["src/main/clojure"]
                         :test-paths ^:replace ["src/test/clojure"]
                         :resource-paths ^:replace ["src/module/resources" "src/test/resources"]
                         :java-source-paths ^:replace ["src/main/java"]
                         :jar-exclusions [#"\.java$"]

                         ;; This is occasionally broken due to
                         ;; https://github.com/technomancy/leiningen/issues/878 
                         :aliases ^:replace {"all" ["do" "clean," "test," "install"]}}

             :versions {org.clojure/clojure             "1.5.1"
                        leiningen-core/leiningen-core   "2.3.4"
                        org.infinispan/infinispan-core  "6.0.0.Final"

                        :immutant                       "1.0.3-SNAPSHOT"
                        :ring                           "1.2.1"
                        :jbossas                        "7.2.x.slim.incremental.12"
                        :polyglot                       "1.x.incremental.61"
                        
                        org.immutant/immutant-parent               :immutant
                        org.immutant/immutant-modules-parent       :immutant
                        org.immutant/immutant-core-module          :immutant
                        org.immutant/immutant-common-module        :immutant
                        org.immutant/immutant-common               :immutant
                        org.immutant/immutant-xa-module            :immutant
                        org.immutant/immutant-bootstrap-module     :immutant
                        org.immutant/immutant-clojure-test-support :immutant
                        org.immutant/immutant-as-test-support      :immutant

                        org.jboss.as/jboss-as-server    :jbossas
                        org.jboss.as/jboss-as-jmx       :jbossas
                        org.jboss.as/jboss-as-messaging :jbossas
                        org.jboss.as/jboss-as-web       :jbossas
                        
                        ring/ring-servlet :ring
                        ring/ring-devel   :ring
                        
                        org.projectodd/polyglot-core        :polyglot
                        org.projectodd/polyglot-xa          :polyglot
                        org.projectodd/polyglot-web         :polyglot
                        org.projectodd/polyglot-jobs        :polyglot
                        org.projectodd/polyglot-cache       :polyglot
                        org.projectodd/polyglot-hasingleton :polyglot
                        org.projectodd/polyglot-messaging   :polyglot}})
