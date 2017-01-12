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

(ns immutant.web.undertow-test
  (:require [clojure.test :refer :all]
            [immutant.web.undertow :refer :all])
  (:import [io.undertow Undertow$Builder UndertowOptions]
           [org.xnio Options SslClientAuthMode]))

(defn reflect
  [field-name instance]
  (-> (class instance)
    (.getDeclaredField field-name)
    (doto (.setAccessible true))
    (.get instance)))

(deftest undertow-options
  (let [m {:host "hostname"
           :port 42
           :ssl-port 443
           :ajp-port 9999
           :io-threads 1
           :worker-threads 2
           :buffer-size 3
           :buffers-per-region 4
           :direct-buffers? false}
        v (options m)]
    (is (:configuration v))
    (is (= {:host "hostname" :port 42} (select-keys v (keys m))))
    (are [x expected] (= expected (reflect x (:configuration v)))
         "ioThreads"        1
         "workerThreads"    2
         "bufferSize"       3
         "buffersPerRegion" 4
         "directBuffers"    false)
    (is (= #{"AJP" "HTTP" "HTTPS"}
          (->> v
            :configuration
            (reflect "listeners")
            (map (comp str (partial reflect "type")))
            set))))
  ;; Make sure kwargs and true :direct-buffers works
  (let [v (:configuration (options :io-threads 44 :direct-buffers? true))]
    (is (= 44 (reflect "ioThreads" v)))
    (is (= true (reflect "directBuffers" v))))
  ;; Make sure only valid arguments accepted
  (is (thrown? IllegalArgumentException (options :this-should-barf 42))))

(deftest undertow-options-as-strings
  (let [m {"host" "hostname"
           ":port" "42"
           "--ssl-port" "443"
           "-ajp-port" "9999"
           "--io-threads" "1"
           "+worker-threads" "2"
           "++buffer-size" "3"
           "-buffers-per-region" "4"
           "--direct-buffers?" "true"}
        v (options m)]
    (is (:configuration v))
    (is (every? (set (keys v)) [:host :port :configuration]))
    (are [x expected] (= expected (reflect x (:configuration v)))
         "ioThreads"        1
         "workerThreads"    2
         "bufferSize"       3
         "buffersPerRegion" 4
         "directBuffers"    true)
    (is (= #{"AJP" "HTTP" "HTTPS"}
          (->> v
            :configuration
            (reflect "listeners")
            (map (comp str (partial reflect "type")))
            set))))
  (let [v (:configuration (options "--io-threads" "44" ":direct-buffers?" "false"))]
    (is (= 44 (reflect "ioThreads" v)))
    (is (= false (reflect "directBuffers" v))))
  (is (thrown? IllegalArgumentException (options "--this-should-barf" "42"))))

(deftest client-authentication
  (are [in out] (= out (let [c (:configuration (options :client-auth in))]
                         (-> (reflect "socketOptions" c)
                           .getMap
                           (.get Options/SSL_CLIENT_AUTH_MODE))))
       :want      SslClientAuthMode/REQUESTED
       :requested SslClientAuthMode/REQUESTED
       :need      SslClientAuthMode/REQUIRED
       :required  SslClientAuthMode/REQUIRED)
  (is (thrown? IllegalArgumentException (options :client-auth :invalid))))

(deftest enable-http2
  (let [builder (:configuration (options :http2? true))
        opts (-> (reflect "serverOptions" builder) .getMap)]
    (is (.get opts UndertowOptions/ENABLE_HTTP2))
    (is (.get opts UndertowOptions/ENABLE_SPDY))))
