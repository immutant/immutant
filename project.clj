;; Copyright 2014 Red Hat, Inc, and individual contributors.
;; 
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; 
;; http://www.apache.org/licenses/LICENSE-2.0
;; 
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

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
                                         "http://repository.jboss.org/nexus/content/groups/public/"]]
                         :dependencies [[org.projectodd.wunderboss/wunderboss-clojure _]]
                         :aliases {"-i" ["with-profile" "+fast"]}

                         :mailing-list {:name "Immutant users list"
                                        :unsubscribe "immutant-users-unsubscribe@immutant.org"
                                        :subscribe "immutant-users-subscribe@immutant.org"
                                        :post "immutant-users@immutant.org"}
                         :url "http://immutant.org"
                         :scm {:name "git", :url "https://github.com/immutant/immutant/"}}

             :versions {org.clojure/clojure             "1.6.0-beta2"
                        org.clojure/java.classpath      "0.2.2"

                        :infinispan                     "6.0.0.Final"
                        :hornetq                        "2.3.1.Final"
                        :immutant                       "2.0.0-SNAPSHOT"
                        :ring                           "1.2.1"
                        :wunderboss                     "0.1.0-SNAPSHOT"

                        org.immutant/core               :immutant
                        org.immutant/immutant-parent    :immutant
                        
                        org.projectodd.wunderboss/wunderboss-clojure      :wunderboss
                        org.projectodd.wunderboss/wunderboss-web          :wunderboss
                        org.projectodd.wunderboss/wunderboss-scheduling   :wunderboss

                        ring/ring-servlet :ring
                        ring/ring-devel   :ring}})
