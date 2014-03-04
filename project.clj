(defproject org.immutant/immutant-parent "2.0.0-SNAPSHOT"
  :description "Parent for all that is Immutant"
  :plugins [[lein-modules "0.2.0"]]
  :packaging "pom"

  :profiles {:provided {:dependencies [[org.clojure/clojure _]]}
             :fast {:modules {:subprocess false}}}
  
  :modules  {:inherited {:repositories [["projectodd-upstream"
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

                         :aliases {"-i" ["with-profile" "+fast"]}

                         :mailing-list {:name "Immutant users list"
                                        :unsubscribe "immutant-users-unsubscribe@immutant.org"
                                        :subscribe "immutant-users-subscribe@immutant.org"
                                        :post "immutant-users@immutant.org"}
                         :url "http://immutant.org"
                         :scm {:name "git", :url "https://github.com/immutant/immutant/"}}

             :versions {org.clojure/clojure             "1.5.1"

                        :infinispan                     "6.0.0.Final"
                        :hornetq                        "2.3.1.Final"
                        :immutant                       "2.0.0-SNAPSHOT"
                        :ring                           "1.2.1"
                        :wunderboss                     "0.1.0-SNAPSHOT"

                        org.immutant/core               :immutant
                        org.immutant/immutant-parent    :immutant
                        
                        org.projectodd.wunderboss/wunderboss-core :wunderboss
                        org.projectodd.wunderboss/wunderboss-web  :wunderboss
                        
                        ring/ring-servlet :ring
                        ring/ring-devel   :ring}})
