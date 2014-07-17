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

(ns testing.hello.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]))

(defn home-page [request] (ring-resp/response "Hello World!"))

(defroutes routes [[["/" {:get home-page}]]])

(def service {::http/routes routes})

(def servlet (::http/servlet (http/create-servlet service)))
