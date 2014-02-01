 (defproject org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  :description "Parent for all that is Immutant"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :packaging "pom"
  :modules  {:inherited {:dependencies [[org.clojure/clojure _]
                                        [org.jboss.as/jboss-as-server _ :scope "provided"]
                                        [midje "1.6.0" :scope "test"]]
                         :repositories [["projectodd-upstream"
                                         {:url "http://repository-projectodd.forge.cloudbees.com/upstream"
                                          :snapshots false}]
                                        ["projectodd-release"
                                         {:url "http://repository-projectodd.forge.cloudbees.com/release"
                                          :snapshots false}]
                                        ["projectodd-snapshot"
                                         {:url "https://repository-projectodd.forge.cloudbees.com/snapshot"
                                          :snapshots true}]
                                        ["jboss"
                                         "https://repository.jboss.org/nexus/content/groups/public-thirdparty-releases/"]]
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
                        cheshire/cheshire               "5.2.0"
                        
                        :immutant                       "1.0.3-SNAPSHOT"
                        :ring                           "1.2.1"
                        :jbossas                        "7.2.x.slim.incremental.14"
                        :polyglot                       "1.19.0"
                        
                        org.immutant/immutant-parent               :immutant
                        org.immutant/immutant-modules-parent       :immutant
                        org.immutant/immutant-support-parent       :immutant
                        org.immutant/immutant-namespaces-parent    :immutant
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
                        
                        org.projectodd/polyglot-core             :polyglot
                        org.projectodd/polyglot-xa               :polyglot
                        org.projectodd/polyglot-web              :polyglot
                        org.projectodd/polyglot-jobs             :polyglot
                        org.projectodd/polyglot-cache            :polyglot
                        org.projectodd/polyglot-hasingleton      :polyglot
                        org.projectodd/polyglot-messaging        :polyglot
                        org.projectodd/polyglot-as-test-support  :polyglot}})
