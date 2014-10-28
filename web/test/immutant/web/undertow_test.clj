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

(ns immutant.web.undertow-test
  (:require [clojure.test :refer :all]
            [immutant.web.undertow :refer :all])
  (:import [io.undertow Undertow$Builder]
           [org.xnio Options SslClientAuthMode]))

(defn reflect
  [field-name instance]
  (-> Undertow$Builder
    (.getDeclaredField field-name)
    (doto (.setAccessible true))
    (.get instance)))

(deftest undertow-options
  (let [v (options
            :host "hostname"
            :port 42
            :io-threads 1
            :worker-threads 2
            :buffer-size 3
            :buffers-per-region 4
            :direct-buffers? false)]
    (is (= [:configuration] (keys v)))
    (are [x expected] (= expected (reflect x (:configuration v)))
         "ioThreads"        1
         "workerThreads"    2
         "bufferSize"       3
         "buffersPerRegion" 4
         "directBuffers"    false))
  ;; Make sure an explicit map and true :direct-buffers works
  (let [v (:configuration (options {:io-threads 44 :direct-buffers? true}))]
    (is (= 44 (reflect "ioThreads" v)))
    (is (= true (reflect "directBuffers" v)))))

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
