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

(ns immutant.web-test
  (:require [clojure.test          :refer :all]
            [clojure.set           :refer :all]
            [immutant.web          :refer :all]
            [immutant.web.internal :refer :all]
            [testing.web           :refer [get-body hello handler]]
            [testing.hello.service :as pedestal])
  (:import clojure.lang.ExceptionInfo
           java.net.ConnectException))

(use-fixtures :each
  (fn [f]
    (try
      (f)
      (finally
        (immutant.util/reset)))))

(def url "http://localhost:8080/")
(def url2 "http://localhost:8081/")

(deftest mount-pedestal-service
  (run pedestal/servlet)
  (is (= "Hello World!" (get-body url))))

(deftest run-returns-default-opts
  (let [opts (run hello)]
    (is (subset? (-> (merge register-defaults create-defaults) keys set)
          (-> opts keys set)))))

(deftest run-returns-passed-opts-with-defaults
  (let [opts (run {:context-path "/abc"} hello)]
    (is (subset? (-> (merge register-defaults create-defaults) keys set)
          (-> opts keys set)))
    (is (= "/abc" (:context-path opts)))))

(deftest run-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (run {:invalid true} hello))))

(deftest stop-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (stop {:invalid true}))))

(deftest stop-without-args-stops-default-context
  (run hello)
  (is (= "hello" (get-body url)))
  (run {:context-path "/howdy"} (handler "howdy"))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop)
  (is (= "howdy" (get-body (str url "howdy"))))
  (let [result (is (thrown? ExceptionInfo (get-body url)))]
    (is (= 404 (-> result ex-data :object :status)))))

(deftest stop-with-context-stops-that-context
  (run hello)
  (is (= "hello" (get-body url)))
  (run {:context-path "/howdy"} (handler "howdy"))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop {:context-path "/howdy"})
  (is (= "hello" (get-body url)))
  (is (= "hello" (get-body (str url "howdy")))))

(deftest stopping-last-handler-stops-the-server
  (let [root-opts (run hello)]
    (is (= "hello" (get-body url)))
    (stop root-opts))
  (is (thrown? ConnectException (get-body url))))

(deftest stop-stops-the-requested-server
  (run hello)
  (is (= "hello" (get-body url)))
  (run {:port 8081} hello)
  (is (= "hello" (get-body url2)))
  (stop {:port 8081})
  (is (= "hello" (get-body url)))
  (is (thrown? ConnectException (get-body url2))))

(deftest stop-stops-the-default-server-even-with-explicit-opts
  (run hello)
  (is (= "hello" (get-body url)))
  (stop {:port 8080})
  (is (thrown? ConnectException (get-body url))))

(deftest run-should-call-init
  (let [p (promise)]
    (run {:init #(deliver p 'init)} hello)
    (is (= 'init (deref p 10000 :fail)))))

(deftest stop-should-call-destroy
  (let [p (promise)]
    (run {:destroy #(deliver p 'destroy)} hello)
    (is (not (realized? p)))
    (stop)
    (is (= 'destroy (deref p 10000 :fail)))))

(deftest string-args-to-run-should-work
  (run {"port" "8081"} hello)
  (is (= "hello" (get-body url2))))

(deftest string-args-to-stop-should-work
  (run hello)
  (run {"context-path" "/howdy"} (handler "howdy"))
  (is (= "hello" (get-body url)))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop {"context-path" "/howdy"})
  (is (= "hello" (get-body (str url "howdy")))))

(deftest run-with-threading
  (-> (run hello)
    (assoc :context-path "/howdy")
    (run (handler "howdy"))
    (assoc :port 8081)
    (run (handler "howdy")))
  (is (= "hello" (get-body url)))
  (is (= "howdy" (get-body (str url "howdy"))))
  (is (= "howdy" (get-body (str url2 "howdy")))))

(deftest stop-should-stop-all-threaded-apps
  (let [everything (-> (run hello)
                     (assoc :context-path "/howdy")
                     (run (handler "howdy"))
                     (merge {:context-path "/" :port 8081})
                     (run (handler "howdy")))]
    (is (true? (stop everything)))
    (is (thrown? ConnectException (get-body url)))
    (is (thrown? ConnectException (get-body url2)))
    (is (not (stop everything)))))

(deftest run-dmc-should-work
  (let [called (promise)]
    (with-redefs [clojure.java.browse/browse-url (fn [_] (deliver called true))]
      (let [result (run-dmc hello)]
        (is (= "hello" (get-body url)))
        (is (deref called 1 false))
        (is (= (run hello) result))))))

(deftest run-dmc-with-threading
  (let [call-count (atom 0)]
    (with-redefs [clojure.java.browse/browse-url (fn [_] (swap! call-count inc))]
      (-> (run-dmc hello)
        (assoc :context-path "/howdy")
        (run-dmc (handler "howdy"))
        (assoc :port 8081)
        (run-dmc (handler "howdy")))
      (is (= 3 @call-count))
      (is (= "hello" (get-body url)))
      (is (= "howdy" (get-body (str url "howdy"))))
      (is (= "howdy" (get-body (str url2 "howdy")))))))

(deftest request-map-entries
  (let [request (atom {})
        handler (comp hello #(swap! request into %))
        server (run handler)]
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
    (is (:body @request))
    (is (map? (:headers @request)))
    (is (< 3 (count (:headers @request))))))
