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

(ns ^{:no-doc true} immutant.web.javax.session
    (:require [immutant.web.internal.ring :refer [->LazyMap]])
    (:import [javax.servlet.http HttpServletRequest]))

(def ring-session-key "ring-session-data")

(defn wrap-servlet-session
  [handler]
  (fn [request]
    (if (contains? request :session)
      (handler request)
      (let [^HttpServletRequest hsr (:servlet-request request)
            data (delay (-> hsr .getSession (.getAttribute ring-session-key)))
            response (handler (->LazyMap (assoc request :session data)))]
        (if-let [data (:session response)]
          (.setAttribute (.getSession hsr) ring-session-key data)
          (when-let [session (.getSession hsr false)]
            (.invalidate session)))
        response))))
