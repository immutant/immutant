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

(ns immutant.web.servlet-test
  (:require [clojure.test :refer :all]
            [testing.web :refer [get-body hello]]
            [immutant.web :refer :all]
            [immutant.web.servlet :refer :all]
            [http.async.client :as http]
            [ring.middleware.session :refer (wrap-session)]
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

(deftest session-invalidation
  (let [http (atom {})
        handler (fn [req]
                  (reset! http (http-session req))
                  (if-not (-> req :session :foo)
                    (-> (response "yay")
                      (assoc :session {:foo "yay"}))
                    (-> (response "boo")
                      (assoc :session nil))))]
    (run (create-servlet handler))
    (is (= "yay" (get-body url)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "boo" (get-body url)))
    (is (thrown? IllegalStateException (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "yay" (get-body url)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (stop)))

(deftest avoid-session-collision
  "Ring's session should take priority"
  (let [http (atom {})
        handler (fn [req]
                  (reset! http (http-session req))
                  (if-not (-> req :session :foo)
                    (-> (response "yay")
                      (assoc :session {:foo "yay"}))
                    (-> (response "boo")
                      (assoc :session nil))))]
    (run (create-servlet (wrap-session handler)))
    (is (= "yay" (get-body url)))
    (is (nil? (-> @http (.getAttribute "ring-session-data"))))
    (is (= "boo" (get-body url)))
    (is (nil? (-> @http (.getAttribute "ring-session-data"))))
    (is (= "yay" (get-body url)))
    (is (nil? (-> @http (.getAttribute "ring-session-data"))))
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

(deftest handshake-headers
  (let [result (promise)
        endpoint (create-endpoint
                   :on-message (fn [ch _]
                                 (deliver result (-> ch
                                                   .getUserProperties
                                                   (get "HandshakeRequest")
                                                   .getHeaders))))]
    (run (attach-endpoint (create-servlet) endpoint))
    (with-open [client (http/create-client)
                socket (http/websocket client "ws://localhost:8080")]
      (http/send socket :text "")
      (is (= "http://localhost:8080" (-> (deref result 1000 {}) (get "Origin") first))))
    (stop)))

(deftest request-map-entries
  (let [request (atom {})
        handler (comp hello #(swap! request into %))
        server (run (create-servlet handler))]
    (get-body (str url "?query=help") {:content-type "text/html; charset=utf-8"})
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
    (is (< 3 (count (:headers @request))))))
