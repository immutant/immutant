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
    (:require [immutant.internal.util :refer [require-resolve]]))

(defn wrap-dev-middleware
  "Assumes ring/ring-devel is available on the classpath"
  [handler]
  (let [wrap-reload           (require-resolve 'ring.middleware.reload/wrap-reload)
        wrap-stacktrace       (require-resolve 'ring.middleware.stacktrace/wrap-stacktrace)
        classpath-directories (require-resolve 'clojure.java.classpath/classpath-directories)]
    (-> handler
      (wrap-reload {:dirs (map str (classpath-directories))})
      wrap-stacktrace)))
