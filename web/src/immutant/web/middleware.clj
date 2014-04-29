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

(ns ^{:no-doc true} immutant.web.middleware
    (:use ring.middleware.stacktrace
          ring.middleware.reload)
    (:require [immutant.util          :as util]
              [clojure.java.classpath :as cp]))

(def ^{:dynamic true} *dev*)

(defn auto-reload?
  "Automatically reload source files?"
  [options]
  (:auto-reload? options *dev*))

(defn stacktraces?
  "Show stacktraces?"
  [options]
  (:stacktraces? options *dev*))

(defn reload-paths
  "Default reload-paths to all directories in the classpath, whether
  they exist at time of deployment or not"
  [options]
  (:reload-paths options (map str (cp/classpath-directories))))

(defn add-stacktraces [handler options]
  (if (stacktraces? options)
    (wrap-stacktrace handler)
    handler))

(defn add-auto-reload [handler options]
  (if (auto-reload? options)
    (wrap-reload handler {:dirs (reload-paths options)})
    handler))

(defn ^{:valid-options #{:auto-reload? :stacktraces? :reload-paths}}
  add-middleware
  [handler options]
  (binding [*dev* (util/dev-mode?)]
    (-> handler
        (add-auto-reload options)
        (add-stacktraces options))))
