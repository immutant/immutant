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
    (:require [ring.middleware.session.store :refer (SessionStore)])
    (:import javax.servlet.http.HttpSession))

(def ring-session-key "ring-session-data")

(def ^:private ^:dynamic ^HttpSession http-session)
(defn bind-http-session
  [handler]
  (fn [request]
    (binding [http-session (-> request :servlet-request .getSession)]
      (handler request))))

(deftype ServletStore []
  SessionStore
  (read-session [_ _]
    (or (.getAttribute http-session ring-session-key) {}))
  (write-session [_ _ data]
    (.setAttribute http-session ring-session-key data)
    (.getId http-session))
  (delete-session [_ _]
    (.removeAttribute http-session ring-session-key)
    (.invalidate http-session)))
