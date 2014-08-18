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

(ns immutant.web.internal.servlet-test
  (:require [clojure.test :refer :all]
            [testing.web :refer [get-body hello]]
            [immutant.web :refer :all]
            [immutant.web.internal.servlet :refer :all]
            [immutant.web.websocket :refer :all]
            [http.async.client :as http]
            [ring.util.response :refer [response]]))

(use-fixtures :each immutant.util/reset-fixture)

(def url "http://localhost:8080/")

(def http-session (comp (memfn getSession) :servlet-request))

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
    (run (create-servlet (wrap-servlet-session handler)))
    (is (= "0" (get-body url)))
    (is (= 1 (:count @ring) (-> @http (.getAttribute "ring-session-data") :count)))
    (is (= "1" (get-body url)))
    (is (= 2 (:count @ring) (-> @http (.getAttribute "ring-session-data") :count)))
    (stop)))

(deftest session-invalidation
  (let [http (atom {})
        handler (fn [req]
                  (reset! http (http-session req))
                  (if-not (-> req :session :foo)
                    (-> (response "yay")
                      (assoc :session {:foo "yay"}))
                    (-> (response "boo")
                      (assoc :session nil))))]
    (run (create-servlet (wrap-servlet-session handler)))
    (is (= "yay" (get-body url)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "boo" (get-body url)))
    (is (thrown? IllegalStateException (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "yay" (get-body url)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (stop)))

(deftest share-session-with-websocket
  (let [result (promise)
        shared (atom nil)
        handler (fn [{s :session}]
                  (if-let [id (:id s)]
                    (-> (swap! shared update-in [id] inc)
                      (get id) str response)
                    (let [id (rand)]
                      (-> (reset! shared {id 0})
                        (get id) str response
                        (assoc :session {:id id})))))]
    (run (create-servlet (wrap-servlet-session handler)
           (create-endpoint {:on-open (fn [ch hs] (let [s (session hs)]
                                                   (reset! shared {(:id s) 41})
                                                   (deliver result s)))})))
    (is (= "0" (get-body url)))
    (is (= "1" (get-body url)))
    (with-open [client (http/create-client)
                socket (http/websocket client "ws://localhost:8080"
                         :cookies @testing.web/cookies)]
      (let [ring-session (deref result 1000 nil)]
        (is (:id ring-session))))
    (is (= "42" (get-body url)))
    (stop)))

(deftest handshake-headers
  (let [result (promise)
        endpoint (create-endpoint :on-open (fn [ch hs] (deliver result hs)))]
    (run (create-servlet nil endpoint))
    (with-open [client (http/create-client)
                socket (http/websocket client "ws://localhost:8080?x=y&j=k")]
      (let [handshake (deref result 1000 nil)]
        (is (not (nil? handshake)))
        (is (= "Upgrade"   (-> handshake headers (get "Connection") first)))
        (is (= "k"         (-> handshake parameters (get "j") first)))
        (is (= "x=y&j=k"   (-> handshake query-string)))
        (is (= "/?x=y&j=k" (-> handshake uri str)))
        (is (false?        (-> handshake (user-in-role? "admin"))))))
    (stop)))

(deftest request-map-entries
  (let [request (atom {})
        handler (comp hello #(swap! request into %))
        server (run (create-servlet (wrap-servlet-session handler)))]
    (get-body (str url "?query=help") :headers {:content-type "text/html; charset=utf-8"})
    (are [x expected] (= expected (x @request))
         :content-type        "text/html; charset=utf-8"
         :character-encoding  "utf-8"
         :remote-addr         "127.0.0.1"
         :server-port         8080
         :content-length      -1
         :uri                 "/"
         :server-name         "localhost"
         :query-string        "query=help"
         :scheme              :http
         :request-method      :get)
    (is (= 5 (count (select-keys @request [:servlet :servlet-request :servlet-response :servlet-context :session]))))
    (is (:body @request))
    (is (map? (:headers @request)))
    (is (< 3 (count (:headers @request))))
    (stop)))
