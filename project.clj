 (defproject org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  :description "Parent for all that is Immutant"
  :plugins [[lein-modules "0.1.1-SNAPSHOT"]]
  :packaging "pom"

  :profiles {:provided
             {:dependencies [[org.clojure/clojure _]
                             [org.jboss.as/jboss-as-server _]]}
             :dev
             {:dependencies [[midje/midje "1.6.0"]]}

             :dist {}}
  
  :modules  {:inherited {:hooks [immutant.build.plugin/hooks]
                         :plugins [[org.immutant/immutant-build-support "1.0.3-SNAPSHOT"]]
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
                         :resource-paths ^:replace ["src/module/resources" "src/test/resources" "src/main/resources"]
                         :java-source-paths ^:replace ["src/main/java"]
                         :jar-exclusions [#"\.java$"]

                         ;; This is occasionally broken due to
                         ;; https://github.com/technomancy/leiningen/issues/878 
                         :aliases ^:displace {"all" ["do" "clean," "test," "install"]}

                         :license {:name "GNU Lesser General Pulic License v2.1"
                                   :url "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"}
                         :mailing-list {:name "Immutant users list"
                                        :unsubscribe "immutant-users-unsubscribe@immutant.org"
                                        :subscribe "immutant-users-subscribe@immutant.org"
                                        :post "immutant-users@immutant.org"}
                         :url "http://immutant.org"
                         :scm {:name "git", :url "https://github.com/immutant/immutant/"}}

             :versions {org.clojure/clojure               "1.5.1"
                        org.clojure/tools.nrepl           "0.2.3"
                        leiningen-core/leiningen-core     "2.3.4"
                        org.infinispan/infinispan-core    "6.0.0.Final"
                        cheshire/cheshire                 "5.2.0"
                        clj-stacktrace/clj-stacktrace     "0.2.7"
                        clojure-complete/clojure-complete "0.2.2"
                        org.tcrawley/dynapath             "0.2.3"
                        
                        :immutant                       "1.0.3-SNAPSHOT"
                        :ring                           "1.2.1"
                        :jbossas                        "7.2.x.slim.incremental.14"
                        :polyglot                       "1.19.0"
                        :hornetq                        "2.3.1.Final"
                        :shimdandy                      "1.0.1"

                        org.hornetq/hornetq-core-client            :hornetq
                        
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
                        org.jboss.as/jboss-as-dist      :jbossas

                        ring/ring-servlet :ring
                        ring/ring-devel   :ring
                        
                        org.projectodd/polyglot-core             :polyglot
                        org.projectodd/polyglot-xa               :polyglot
                        org.projectodd/polyglot-web              :polyglot
                        org.projectodd/polyglot-jobs             :polyglot
                        org.projectodd/polyglot-cache            :polyglot
                        org.projectodd/polyglot-hasingleton      :polyglot
                        org.projectodd/polyglot-messaging        :polyglot
                        org.projectodd/polyglot-as-test-support  :polyglot

                        org.projectodd.shimdandy/shimdandy-api   :shimdandy
                        org.projectodd.shimdandy/shimdandy-impl  :shimdandy}})
