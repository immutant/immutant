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

(ns immutant.web.middleware
  "Ring middleware useful with immutant.web"
  (:require [immutant.internal.util :refer [try-resolve]]
            [immutant.util :refer [in-container?]]
            [immutant.web.internal.servlet :refer [wrap-servlet-session]]
            [immutant.web.internal.undertow :refer [wrap-undertow-session]]))

(defn wrap-development
  "Wraps stacktrace and reload middleware with the correct :dirs
  option set, but will toss an exception if either the passed handler
  isn't a normal Clojure function or ring/ring-devel isn't available
  on the classpath"
  [handler]
  (if-not (or (fn? handler) (and (var? handler) (fn? (deref handler))))
    (throw (RuntimeException. "Middleware only applies to regular Clojure functions")))
  (let [wrap-reload           (try-resolve 'ring.middleware.reload/wrap-reload)
        wrap-stacktrace       (try-resolve 'ring.middleware.stacktrace/wrap-stacktrace)
        classpath-directories (try-resolve 'clojure.java.classpath/classpath-directories)]
    (if wrap-reload
      (-> handler
        (wrap-reload {:dirs (map str (classpath-directories))})
        wrap-stacktrace)
      (throw (RuntimeException. "Middleware requires ring/ring-devel; check your dependencies")))))

(defn wrap-session
  "Uses the session from either Undertow or, when deployed to an app
  server cluster such as WildFly or EAP, the servlet's
  possibly-replicated HttpSession"
  [handler]
  (if (in-container?)
    (wrap-servlet-session handler)
    (wrap-undertow-session handler)))
