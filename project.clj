(defproject org.immutant/immutant-parent "1.1.2-SNAPSHOT"
  :description "Parent for all that is Immutant"
  :plugins [[lein-modules "0.2.4"]]
  :packaging "pom"

  :profiles {:provided
             {:dependencies [[org.clojure/clojure _]
                             [org.jboss.as/jboss-as-server _]]}
             :dev
             {:dependencies [[midje/midje "1.6.3"]]}

             :dist {}
             :integ {}
             :fast {:modules {:subprocess false}}}
  
  :modules  {:inherited {:hooks [immutant.build.plugin.pom/hooks]
                         :plugins [[org.immutant/immutant-build-support "1.1.2-SNAPSHOT"]]
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
                         :aliases {"all" ^:displace ["do" "clean," "test," "install"]
                                   "-i" ["with-profile" "+fast"]}

                         :license {:name "GNU Lesser General Pulic License v2.1"
                                   :url "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"}
                         :mailing-list {:name "Immutant users list"
                                        :unsubscribe "immutant-users-unsubscribe@immutant.org"
                                        :subscribe "immutant-users-subscribe@immutant.org"
                                        :post "immutant-users@immutant.org"}
                         :url "http://immutant.org"
                         :scm {:name "git", :url "https://github.com/immutant/immutant/"}}

             :versions {org.clojure/clojure               "1.6.0"
                        org.clojure/tools.nrepl           "0.2.3"
                        leiningen-core/leiningen-core     "2.3.4"
                        org.infinispan/infinispan-core    "6.0.0.Final"
                        cheshire/cheshire                 "5.2.0"
                        clj-stacktrace/clj-stacktrace     "0.2.7"
                        clojure-complete/clojure-complete "0.2.2"
                        org.tcrawley/dynapath             "0.2.3"
                        
                        :immutant                 "1.1.2-SNAPSHOT"
                        :ring                     "1.2.1"
                        :jbossas                  "7.2.x.slim.incremental.16"
                        :polyglot                 "1.20.0"
                        :hornetq                  "2.3.1.Final"
                        :shimdandy                "1.0.1"

                        ring                      :ring
                        org.hornetq               :hornetq
                        org.immutant              :immutant
                        org.jboss.as              :jbossas
                        org.projectodd            :polyglot
                        org.projectodd.shimdandy  :shimdandy

                        org.immutant/immutant-dependency-exclusions "0.1.0"
                        org.immutant/deploy-tools "0.12.0"
                        org.immutant/fntest "0.5.2"}})
