;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(defproject org.immutant/integs "2.1.5-SNAPSHOT"
  :pedantic? false
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[org.immutant/immutant _]
                 [org.immutant/wildfly _]]
  :aliases {"all" ^:replace ["do" "clean," "test"]}
  :modules {:parent nil}
  :main clojure.core/+                  ; immutant war build requires
                                        ; a main... any no-arg taking
                                        ; fn will do
  :profiles {:integ-base {:plugins [[lein-immutant "2.1.0"]]
                          :aliases {"test" ^:displace ["immutant" "test"]}
                          :modules {:parent ".."}}
             :integ-messaging {:test-paths ["../messaging/test"]}
             :integ-scheduling {:dependencies [[clj-time _]]
                                :test-paths ["../scheduling/test"]}
             :integ-caching {:dependencies [[cheshire _]
                                            [org.clojure/data.fressian _]
                                            [org.clojure/core.memoize _]]
                             :test-paths ["../caching/test"]}
             :integ-web {:dependencies [[io.pedestal/pedestal.service _]
                                        [org.clojars.jcrossley3/http.async.client _]
                                        [org.clojars.tcrawley/gniazdo _]
                                        [ring/ring-devel _]
                                        [compojure _]
                                        [org.glassfish.jersey.media/jersey-media-sse _
                                         :exclusions [org.glassfish.jersey.core/jersey-server]]
                                        [org.glassfish.jersey.core/jersey-client _]
                                        [javax.ws.rs/javax.ws.rs-api "2.0.1"]]
                         :resource-paths ["../web/dev-resources"]
                         :test-paths ["../web/test-integration"]
                         :main integs.web}
             :integ-transactions {:test-paths ["../transactions/test"]
                                  :dependencies [[org.clojure/java.jdbc _]
                                                 [com.h2database/h2 _]]}

             :web [:integ-base :integ-web]
             :scheduling [:integ-base :integ-scheduling]
             :messaging [:integ-base :integ-messaging]
             :caching [:integ-base :integ-caching]
             :transactions [:integ-base :integ-transactions]

             :integs [:web :messaging :caching :scheduling :transactions]

             :cluster {:eval-in :leiningen ; because prj/read, lein-modules, hooks, etc
                       :modules {:parent ".."}
                       :main integs.cluster
                       :dependencies [[org.immutant/fntest _]
                                      [clj-http _]
                                      [environ _]
                                      [jboss-as-management "0.4.3-SNAPSHOT"]]
                       :plugins [[lein-environ "1.0.0"]]
                       :test-paths ^:replace ["test-clustering"]}
             :eap-base {:env {:eap true}
                        :immutant {:war {:resource-paths ["eap-resources"]}}
                        :exclusions [org.hornetq/hornetq-jms-server org.hornetq/hornetq-server org.jboss.narayana.jta/narayana-jta]
                        :dependencies [[org.hornetq/hornetq-jms-server "2.3.25.Final"]
                                       [org.hornetq/hornetq-server "2.3.25.Final"]
                                       [org.jboss.jbossts.jta/narayana-jta "4.17.29.Final"]]}
             :eap [:web :scheduling :messaging :caching :transactions :eap-base]})
