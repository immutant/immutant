;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(defproject org.immutant/integs "2.0.2-SNAPSHOT"
  :pedantic? false
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[org.immutant/immutant _]
                 [org.immutant/wildfly _]]
  :aliases {"all" ^:replace ["do" "clean," "test"]}
  :modules {:parent nil}
  :profiles {:integ-base {:plugins [[lein-immutant "2.0.0"]]
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
                                        [stylefruits/gniazdo _]
                                        [ring/ring-devel _]
                                        [compojure _]
                                        [org.glassfish.jersey.media/jersey-media-sse _]]
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
                                      [clj-http _]]
                       :test-paths ^:replace ["test-clustering"]}})
