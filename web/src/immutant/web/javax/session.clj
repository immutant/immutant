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
    (:require [ring.middleware.session :refer (wrap-session)]
              [ring.middleware.session.store :refer (SessionStore)]
              [immutant.web.javax :refer (session)]))

(def ring-session-key "ring-session-data")

(def ^{:private true :dynamic true} current-request)
(defn ^{:private true} set-current-request
  [handler]
  (fn [request]
    (binding [current-request request]
      (handler request))))

(deftype ServletStore []
  SessionStore
  (read-session [_ _]
    (or (.getAttribute (session current-request) ring-session-key) {}))
  (write-session [_ _ data]
    (let [s (session current-request)]
      (.setAttribute s ring-session-key data)
      (.getId s)))
  (delete-session [_ _]
    (let [s (session current-request)]
      (.removeAttribute s ring-session-key)
      (.invalidate s))))

(defn wrap-session
  "Store the ring session data in the servlet's HttpSession"
  [handler]
  (-> handler
    (wrap-session {:store (->ServletStore)})
    set-current-request))
