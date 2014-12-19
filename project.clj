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
  :pedantic? false
  :plugins [[lein-modules "0.3.10"]]
  :packaging "pom"

  :profiles {:pedantic {:pedantic? true}
             :provided {:dependencies [[org.clojure/clojure _]]}
             :travis {:modules {:subprocess "lein2"}}
             :incremental {:deploy-repositories [["releases"
                                                  {:url "dav:https://repository-projectodd.forge.cloudbees.com/incremental"
                                                   :sign-releases false}]]
                           :plugins [[lein-webdav "0.1.0"]]}
             :dev {:dependencies [[pjstadig/humane-test-output "0.6.0"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             :integs {}}

  :aliases {"docs-from-index" ["build-helper" "docs" "generate" "docs/guides"
                               "caching" "core" "messaging" "scheduling" "transactions" "web" "wildfly"]
            "docs" ["do" "modules" "doc-index" "," "docs-from-index"]}
  :modules  {:subprocess nil
             :inherited {:repositories [["projectodd-upstream"
                                         {:url "http://repository-projectodd.forge.cloudbees.com/upstream"
                                          :snapshots false}]
                                        ["projectodd-release"
                                         {:url "http://repository-projectodd.forge.cloudbees.com/release"
                                          :snapshots false}]
                                        ["projectodd-snapshot"
                                         {:url "http://repository-projectodd.forge.cloudbees.com/snapshot"

                                          :snapshots true}]
                                        ["projectodd-incremental"
                                         {:url "https://repository-projectodd.forge.cloudbees.com/incremental"
                                          :snapshots false}]
                                        ["jboss"
                                         "http://repository.jboss.org/nexus/content/groups/public/"]]
                         :dependencies [[org.projectodd.wunderboss/wunderboss-clojure _]
                                        [org.clojure/clojure _]]
                         :aliases {"-i" ^:replace ["with-profile" "+integs"]
                                   "doc-index" ^:replace ["build-helper" "docs" "generate-index"]
                                   "all" ^:displace ["do" "clean," "check," "test," "install"]}

                         :mailing-list {:name "Immutant users list"
                                        :unsubscribe "immutant-users-unsubscribe@immutant.org"
                                        :subscribe "immutant-users-subscribe@immutant.org"
                                        :post "immutant-users@immutant.org"}
                         :url "http://immutant.org"
                         :scm {:dir "."}
                         :license {:name "Apache Software License - v 2.0"
                                   :url "http://www.apache.org/licenses/LICENSE-2.0"
                                   :distribution :repo}
                         :plugins [[org.immutant/build-helper "0.2.8"]]
                         :hooks [build-helper.plugin.pom/hooks]

                         :signing {:gpg-key "BFC757F9"}
                         :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]}

             :versions {clojure                    "1.6.0"
                        java.classpath             "0.2.2"
                        tools.nrepl                "0.2.3"
                        tools.reader               "0.8.4"
                        ring                       "1.3.0"
                        clj-time                   "0.7.0"
                        cheshire                   "5.3.1"
                        data.fressian              "0.2.0"
                        core.memoize               "0.5.6"
                        io.pedestal                "0.3.0"
                        http.async.client          "0.5.2"
                        stylefruits/gniazdo        "0.1.0"
                        compojure                  "1.1.8"
                        org.clojure/java.jdbc      "0.3.5"
                        h2                         "1.3.176"

                        ;; org.projectodd.wunderboss  "0.3.0"
                        org.projectodd.wunderboss  "1.x.incremental.176"
                        ;; org.projectodd.wunderboss  "0.4.0-SNAPSHOT"

                        org.immutant               :version}}

  :release-tasks  [["vcs" "assert-committed"]
                   ["change"           "version" "set" "\"2.0.0-beta1\""]
                   ["modules" "change" "version" "set" "\"2.0.0-beta1\""]
                   ["vcs" "commit"]
                   ["vcs" "tag"]
                   ["modules" "deploy"]
                   ["change"           "version" "set" "\"2.0.0-SNAPSHOT\""]
                   ["modules" "change" "version" "set" "\"2.0.0-SNAPSHOT\""]
                   ["vcs" "commit"]
                   ["vcs" "push"]])
