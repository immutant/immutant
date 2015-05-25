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

(defproject org.immutant/build-helper "0.2.8"
  :description "A plugin to aid in building Immutant"
  :pedantic? false
  :url "https://github.com/immutant/immutant"
  :license {:name "Apache Software License - v 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}
  :dependencies [[codox/codox.core "0.8.10"]
                 [robert/hooke "1.3.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :eval-in-leiningen true
  :signing {:gpg-key "BFC757F9"})
