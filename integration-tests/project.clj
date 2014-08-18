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
  :plugins [[lein-modules "0.3.8"]]
  :packaging "pom"
  :dependencies [[org.clojure/clojure _]]

  :modules {:parent nil
            :inherited {:plugins [[lein-immutant "2.0.0-SNAPSHOT"]]
                        :aliases {"all"  ^:replace ["do" "clean," "test"]
                                  "test" ^:displace ["immutant" "test"]}}}

  :aliases {"test" "do"}
  :profiles {:default [:base :system :user :provided :dev :integs]
             :integs {:modules {:parent ".."}}})
