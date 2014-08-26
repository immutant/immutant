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

(defproject org.immutant/integs "2.0.0-SNAPSHOT"
  :plugins [[lein-modules "0.3.8"]
            [lein-immutant "2.0.0-alpha1"]]
  :dependencies [[org.immutant/immutant _]
                 [org.immutant/wildfly _]]
  :aliases {"all" ^:replace ["do" "clean," "test"]}
  :modules {:parent nil}
  :profiles {:integ-base {:aliases {"test" ^:displace ["immutant" "test"]}
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
                                        [compojure _]]
                         :resource-paths ["../web/dev-resources"]
                         :test-paths ["../web/test-integration"]
                         :main integs.web}

             :web [:integ-base :integ-web]
             :scheduling [:integ-base :integ-scheduling]
             :messaging [:integ-base :integ-messaging]
             :caching [:integ-base :integ-caching]

             :integs [:web :messaging :caching :scheduling]})
