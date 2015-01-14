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

(ns build-helper.docs
  (:require [leiningen.core.eval    :as eval]
            [build-helper.docs.util :as u]
            [clojure.java.io        :as io])
  (:import java.util.Properties))

(def version
  (.getProperty
    (doto (Properties.)
      (.load (->> "META-INF/maven/org.immutant/build-helper/pom.properties"
               io/resource
               io/reader)))
    "version"))

(defn generate-index [project]
  (eval/eval-in-project
    (update-in project [:dependencies] conj ['org.immutant/build-helper version])
    `(build-helper.docs.util/generate-index
       ~(:root project)
       ~(:target-path project)
       '~(:source-paths project))
    `(require 'build-helper.docs.util)))

(defn doc-task [project subtask & args]
  (case subtask
    "generate-index" (generate-index project)
    "generate"       (u/generate-docs {:root-path (:root project)
                                       :target-path (:target-path project)
                                       :version (:version project)
                                       :versions (get-in project [:modules :versions])
                                       :guides-dir (first args)
                                       :base-dirs (rest args)})))
