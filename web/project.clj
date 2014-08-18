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

(defproject org.immutant/web "2.0.0-SNAPSHOT"
  :plugins [[lein-modules "0.3.8"]]

  :dependencies [[org.immutant/core _]
                 [potemkin "0.3.4"]
                 [org.projectodd.wunderboss/wunderboss-web _]]

  :profiles {:dev
             {:dependencies [[io.pedestal/pedestal.service _]
                             [org.clojars.jcrossley3/http.async.client _]
                             [stylefruits/gniazdo _]
                             [ring/ring-devel _]]}})
