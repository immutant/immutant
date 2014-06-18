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
  :plugins [[lein-modules "0.3.4"]]
  :packaging "pom"

  :modules {:parent nil
            :packaging "pom"
            :inherited {:aliases
                        {"all" ^:displace ["do" "clean," "test"]}
                        :dependencies [[org.clojure/clojure "1.6.0"]]}}

  :profiles {:integs
             {:modules {:parent ".."}}})
