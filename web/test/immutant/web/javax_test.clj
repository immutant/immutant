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

(ns immutant.web.javax-test
  (:require [clojure.test :refer :all]
            [testing.web :refer [get-body]]
            [immutant.web :refer :all]
            [immutant.web.javax :refer :all]
            [http.async.client :as http]
            [ring.util.response :refer [response]]))

(use-fixtures :each
  (fn [f]
    (try
      (f)
      (finally
        (immutant.util/reset)))))

(def url "http://localhost:8080/")

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str count))
        (assoc :session session))))

(deftest http-session-store
  (let [ring (atom {})
        http (atom {})
        handler (fn [req]
                  (reset! http (http-session req))
                  (let [res (counter req)]
                    (reset! ring (:session res))
                    res))]
    (run (create-servlet handler))
    (is (= "0" (get-body url)))
    (is (= 1 (:count @ring) (-> @http (.getAttribute "ring-session-data") :count)))
    (is (= "1" (get-body url)))
    (is (= 2 (:count @ring) (-> @http (.getAttribute "ring-session-data") :count)))
    (stop)))

(deftest share-session-with-websocket
  (let [latch (promise)
        servlet (create-servlet counter)]
    (run (attach-endpoint servlet
           (create-endpoint {:on-open (fn [ch]
                                        (-> ch
                                          .getUserProperties
                                          (get "HandshakeRequest")
                                          .getHttpSession
                                          (.setAttribute "ring-session-data" {:count 42}))
                                        (deliver latch :success))})))
    (is (= "0" (get-body url)))
    (is (= "1" (get-body url)))
    (with-open [client (http/create-client)
                socket (http/websocket client "ws://localhost:8080"
                         :cookies @testing.web/cookies
                         :open (fn [_] (deref latch 1000 :fail)))])
    (is (= "42" (get-body url)))
    (stop)))
