;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
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

(ns build-helper.plugin.pom
  (:require [robert.hooke]
            [leiningen.pom]
            [leiningen.test]))

(defn put-pom-in-target [f & args]
  (let [[project pom] args]
    (if (= pom "pom.xml")
      (f project "target/pom.xml")
      (apply f args))))

(defn skip-tests-for-pom-packages [f & args]
  (let [[project] args]
    (if-not (= "pom" (:packaging project))
      (apply f args))))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.pom/pom #'put-pom-in-target)
  (robert.hooke/add-hook #'leiningen.test/test #'skip-tests-for-pom-packages))
