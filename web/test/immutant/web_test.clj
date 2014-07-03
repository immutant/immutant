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

(deftest run-takes-kwargs
  (run hello :path "/kwarg")
  (is (= "hello" (get-body (str url "kwarg")))))

(deftest run-returns-default-opts
  (let [opts (run hello)]
    (is (subset? (-> (merge register-defaults create-defaults) keys set)
          (-> opts keys set)))))


(deftest run-returns-passed-opts-with-defaults
  (let [opts (run hello {:path "/abc"})]
    (is (subset? (-> (merge register-defaults create-defaults) keys set)
          (-> opts keys set)))
    (is (= "/abc" (:path opts)))))

(deftest run-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (run hello {:invalid true}))))

(deftest stop-should-throw-with-invalid-options
  (is (thrown? IllegalArgumentException (stop {:invalid true}))))

(deftest stop-without-args-stops-default-context
  (run hello)
  (is (= "hello" (get-body url)))
  (run (handler "howdy") {:path "/howdy"})
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop)
  (is (= "howdy" (get-body (str url "howdy"))))
  (is (= 404 (get-body url))))

(deftest stop-with-context-stops-that-context
  (run hello)
  (is (= "hello" (get-body url)))
  (run (handler "howdy") {:path "/howdy"})
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop {:path "/howdy"})
  (is (= "hello" (get-body url)))
  (is (= "hello" (get-body (str url "howdy")))))

(deftest stop-should-accept-kwargs
  (run hello)
  (is (stop :path "/")))

(deftest stopping-last-handler-stops-the-server
  (let [root-opts (run hello)]
    (is (= "hello" (get-body url)))
    (stop root-opts))
  (is (thrown? ConnectException (get-body url))))

(deftest stop-stops-the-requested-server
  (run hello)
  (is (= "hello" (get-body url)))
  (run hello {:port 8081})
  (is (= "hello" (get-body url2)))
  (stop {:port 8081})
  (is (= "hello" (get-body url)))
  (is (thrown? ConnectException (get-body url2))))

(deftest stop-stops-the-default-server-even-with-explicit-opts
  (run hello)
  (is (= "hello" (get-body url)))
  (stop {:port 8080})
  (is (thrown? ConnectException (get-body url))))

(deftest string-args-to-run-should-work
  (run hello {"port" "8081"})
  (is (= "hello" (get-body url2))))

(deftest string-args-to-stop-should-work
  (run hello)
  (run (handler "howdy") {"path" "/howdy"})
  (is (= "hello" (get-body url)))
  (is (= "howdy" (get-body (str url "howdy"))))
  (stop {"path" "/howdy"})
  (is (= "hello" (get-body (str url "howdy")))))

(deftest run-with-threading
  (-> (run hello)
    (assoc :path "/howdy")
    (->> (run (handler "howdy")))
    (assoc :port 8081)
    (->> (run (handler "howdy"))))
  (is (= "hello" (get-body url)))
  (is (= "howdy" (get-body (str url "howdy"))))
  (is (= "howdy" (get-body (str url2 "howdy")))))

(deftest stop-should-stop-all-threaded-apps
  (let [everything (-> (run hello)
                     (assoc :path "/howdy")
                     (->> (run (handler "howdy")))
                     (merge {:path "/" :port 8081})
                     (->> (run (handler "howdy"))))]
    (is (true? (stop everything)))
    (is (thrown? ConnectException (get-body url)))
    (is (thrown? ConnectException (get-body url2)))
    (is (not (stop everything)))))

(deftest run-dmc-should-work
  (let [called (promise)]
    (with-redefs [clojure.java.browse/browse-url (fn [u] (deliver called u))]
      (let [result (run-dmc hello :path "/hello")
            uri (str url "hello")]
        (is (= "hello" (get-body uri)))
        (is (= uri (deref called 1 false)))
        (is (= (run hello :path "/hello") result))))))

(deftest run-dmc-should-take-kwargs
  (let [called (promise)]
    (with-redefs [clojure.java.browse/browse-url (fn [_] (deliver called true))]
      (run-dmc hello :path "/foo")
      (is (= "hello" (get-body (str url "foo"))))
      (is (deref called 1 false)))))

(deftest run-dmc-with-threading
  (let [call-count (atom 0)]
    (with-redefs [clojure.java.browse/browse-url (fn [_] (swap! call-count inc))]
      (-> (run-dmc hello)
        (assoc :path "/howdy")
        (->> (run-dmc (handler "howdy")))
        (assoc :port 8081)
        (->> (run-dmc (handler "howdy"))))
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

(deftest virtual-hosts
  (let [all (-> (run hello :virtual-host ["integ-app1.torquebox.org" "integ-app2.torquebox.org"])
              (assoc :virtual-host "integ-app3.torquebox.org")
              (->> (run (handler "howdy"))))]
    (is (= "hello" (get-body "http://integ-app1.torquebox.org:8080/")))
    (is (= "hello" (get-body "http://integ-app2.torquebox.org:8080/")))
    (is (= "howdy" (get-body "http://integ-app3.torquebox.org:8080/")))
    (is (= 404 (get-body url)))
    (is (true? (stop :virtual-host "integ-app1.torquebox.org")))
    (is (= 404 (get-body "http://integ-app1.torquebox.org:8080/")))
    (is (= "hello" (get-body "http://integ-app2.torquebox.org:8080/")))
    (is (= "howdy" (get-body "http://integ-app3.torquebox.org:8080/")))
    (is (true? (stop all)))
    (is (thrown? ConnectException (get-body "http://integ-app2.torquebox.org:8080/")))
    (is (thrown? ConnectException (get-body "http://integ-app3.torquebox.org:8080/")))
    (is (nil? (stop all)))))
