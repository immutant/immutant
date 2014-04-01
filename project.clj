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
  :plugins [[lein-modules "0.2.1"]]
  :packaging "pom"

  :profiles {:provided {:dependencies [[org.clojure/clojure _]]}
             :fast {:modules {:subprocess false}}
             :incremental {:deploy-repositories [["release"
                                                   {:url "dav:https://repository-projectodd.forge.cloudbees.com/incremental"
                                                    :username :env/bees_username
                                                    :password :env/bees_password}]]
                           :plugins [[lein-webdav "0.1.0"]]}}
  
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

             :versions {clojure                    "1.6.0"
                        java.classpath             "0.2.2"
                        tools.nrepl                "0.2.3"
                        ring                       "1.2.1"
                        
                        org.immutant               "2.0.0-SNAPSHOT"
                        org.projectodd.wunderboss  "1.x.incremental.3"}})
  
