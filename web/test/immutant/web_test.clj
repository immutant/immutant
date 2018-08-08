;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
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
            [immutant.util         :as u]
            [immutant.web          :refer :all]
            [immutant.web.internal.wunderboss :refer [create-defaults register-defaults]]
            [immutant.web.middleware :refer (wrap-session)]
            [testing.web           :refer [get-body get-response hello handler file-response
                                           input-stream-response seq-response]]
            [testing.app]
            [testing.hello.service :as pedestal]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :refer (charset)]
            [clj-http.client :as http]
            [immutant.web.undertow :as undertow])
  (:import clojure.lang.ExceptionInfo
           java.net.ConnectException
           javax.servlet.Filter))

(u/set-log-level! (or (System/getenv "LOG_LEVEL") :ERROR))

(use-fixtures :each u/reset-fixture)

(def url "http://localhost:8080/")
(def url2 "http://localhost:8081/")

(defn pause-on-windows []
  ;; windows is slow to release closed ports, so we pause to allow that to happen
  (when (re-find #"(?i)^windows" (System/getProperty "os.name"))
    (Thread/sleep 100)))

(deftest mount-remount-and-share-pedestal-service
  (run pedestal/servlet)
  (is (= "Hello World!" (get-body url)))
  (run pedestal/servlet)
  (is (= "Hello World!" (get-body url)))
  (run hello :path "/some-path")
  (is (= "Hello World!" (get-body url)))
  (is (= "hello" (get-body (str url "some-path")))))

(deftest nil-body
  (run (constantly {:status 200 :body nil}))
  (is (nil? (get-body url))))

(deftest hashcode-on-request-works
  (run #(try
          (.hashCode %)
          {:status 200
           :body "success"}
          (catch Exception e
            (.printStackTrace e)
            {:status 200
             :body "failure"})))
  (is (= "success" (get-body url))))

(deftest run-takes-kwargs
  (run hello :path "/kwarg")
  (is (= "hello" (get-body (str url "kwarg")))))

(deftest run-returns-default-opts
  (let [opts (run hello)]
    (is (subset? (-> (merge register-defaults create-defaults) keys set)
          (-> opts keys set)))))

(deftest run-accepts-a-var
  (run #'hello)
  (is (= "hello" (get-body url))))

(deftest run-accepts-an-http-handler
  (let [hh (undertow/http-handler hello)]
    (is (instance? io.undertow.server.HttpHandler hh))
    (run hh)
    (is (= "hello" (get-body url)))))

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

(deftest run-with-no-dispatch-should-work
  (run hello :dispatch? false)
  (is (= "hello" (get-body url))))

(deftest writing-a-file-with-no-dispatch-should-work
  (run file-response :dispatch? false)
  (is (= "foo" (get-body url))))

(deftest writing-a-file-with-dispatch-should-work
  (run file-response)
  (is (= "foo" (get-body url))))

(deftest writing-an-input-stream-with-no-dispatch-should-work
  (run input-stream-response :dispatch? false)
  (is (= "foo" (get-body url))))

(deftest writing-an-input-stream-with-dispatch-should-work
  (run input-stream-response)
  (is (= "foo" (get-body url))))

(deftest writing-a-seq-with-no-dispatch-should-work
  (run seq-response :dispatch? false)
  (is (= "abcfoo" (get-body url))))

(deftest writing-a-seq-with-dispatch-should-work
  (run seq-response)
  (is (= "abcfoo" (get-body url))))

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
         :request-method      :get
         :protocol            "HTTP/1.1")
    (is (:body @request))
    (is (map? (:headers @request)))
    (is (< 3 (count (:headers @request))))))

;; IMMUTANT-533
(deftest find-should-work-on-request-map
  (run (fn [req] {:status 200 :body (pr-str (find req :scheme))}))
  (is (= [:scheme :http] (read-string (get-body url)))))

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

(deftest relative-resource-paths
  (run (-> hello (wrap-resource "public")))
  (is (= "foo" (get-body (str url "foo.html"))))
  (stop)
  (pause-on-windows)
  (run (-> hello (wrap-resource "public")) :path "/foo")
  (is (= "foo" (get-body (str url "foo/foo.html"))))
  (is (= "hello" (get-body (str url "foo")))))

(deftest servers
  (let [srv (server)]
    (is (every? (partial identical? srv)
          [(server :port 8080)
           (server {:port 8080})
           (server (run hello))]))
    (is (.isRunning srv))
    (.stop srv)
    (is (not (.isRunning srv)))
    (pause-on-windows)
    (.start srv)
    (is (.isRunning srv))
    (is (= "hello" (get-body url)))))

(deftest https
  (run hello
    :ssl-port     8443
    :keystore     "dev-resources/keystore.jks"
    :key-password "password")
  (let [response (http/get "https://localhost:8443" {:insecure? true})]
    (is (= (:status response) 200))
    (is (= (:body response) "hello"))))

(deftest encoding
  (run (fn [r] (charset ((handler "ɮѪϴ") r) "UTF-16")))
  (is (= "ɮѪϴ" (:body (http/get url {:as :auto})))))

(deftest session-sans-web-context
  (let [request {:uri "/" :request-method :get}]
    (is (= "0" (:body (testing.app/routes request))))
    (is (= "0" (:body ((-> testing.app/routes wrap-session) request))))
    (is (get-in ((-> testing.app/routes wrap-session) request) [:headers "Set-Cookie"]))))

(deftest cookie-attributes
  (run (-> (fn [r] (:session r) (hello {}))
         (wrap-session {:cookie-name "foo"
                        :cookie-attrs {:path "/foo" :domain "foo.com" :max-age 20 :http-only true}})))
  (get-response url :cookies nil)
  (let [c @testing.web/cookies]
    (is (= 1 (count c)))
    (is (= "foo" (-> c first :name)))
    (is (= "/foo" (-> c first :path)))
    (is (= "foo.com" (-> c first :domain)))
    (is (= "20" (-> c first :Max-Age)))))

(deftest zero-for-available-port
  (let [x1 (run (handler "one") :port 0)
        x2 (run (handler "two") :port 0)
        p1 (:port x1)
        p2 (:port x2)]
    (is (not= p1 p2))
    (is (not-any? zero? [p1 p2]))
    (is (= "one" (get-body (str "http://localhost:" p1))))
    (is (= "two" (get-body (str "http://localhost:" p2))))))

(deftest verify-filter-map
  (let [filter (reify Filter
                 (doFilter [_ request response chain]
                   (.doFilter chain request response))
                 (init [_ config])
                 (destroy [_]))]
    (run pedestal/servlet :filter-map {"myfilter" filter})
    (is (= "Hello World!" (get-body url)))))

(deftest undertow-options-should-return-last-port
  (let [server (run hello :port 0 :io-threads 2)
        port (:port server)
        server (run (handler "howdy") (assoc server :path "/howdy"))]
    (try
      (is (= "hello" (get-body (str "http://localhost:" port "/"))))
      (is (= "howdy" (get-body (str "http://localhost:" port "/howdy"))))
      (is (= port (:port server)))
      (finally (stop server)))))
