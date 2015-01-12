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

(ns immutant.web.async-test
  (:require [immutant.web.async :refer :all]
            [clojure.test       :refer :all]
            [testing.web        :refer [get-body get-response]]
            [immutant.web       :as web]
            [immutant.web.internal.servlet :as servlet]
            [immutant.util      :as u]))


(u/set-log-level! (or (System/getenv "LOG_LEVEL") :INFO))

(use-fixtures :each u/reset-fixture)

(defn rand-data [len]
  (clojure.string/join (repeatedly len #(rand-int 10))))

(deftest chunked-streaming
  (let [data (rand-data 128)]
    (web/run
      #(as-channel %
         {:on-open
          (fn [stream]
            (.start
              (Thread.
                (fn []
                  (send! stream "[" false)
                  (dotimes [n 10]
                    ;; we have to send a few bytes with each
                    ;; response - there is a min-bytes threshold to
                    ;; trigger data to the client
                    (send! stream (format "%s ;; %s\n" n data) false))
                   ;; 2-arity send! closes the stream
                  (send! stream "]")))))})))

  ;; TODO: come up with a way to make sure the data actually comes in as chunks
  (let [response (get-response "http://localhost:8080/")]
    (is (= 200 (:status response)))
    (is (= "chunked" (-> response :headers :transfer-encoding)))
    (is (= (range 10) (-> response :body read-string)))))

(deftest non-chunked-stream
  (let [data (rand-data 128)]
    (web/run
      #(as-channel %
         {:on-open
          (fn [stream]
            (send! stream data))}))

    (let [response (get-response "http://localhost:8080/")]
      (is (= 200 (:status response)))
      (is (empty? (-> response :headers :transfer-encoding)))
      (is (= (count data) (-> response :headers :content-length read-string)))
      (is (= data (:body response))))))

(deftest chunked-streaming-servlet
  (let [data (rand-data 128)]
    (web/run
      (servlet/create-servlet
        #(as-channel %
           {:on-open
            (fn [stream]
              (println "TC:" %)
              (.start
                (Thread.
                  (fn []
                    (send! stream "[" false)
                    (dotimes [n 10]
                      ;; we have to send a few bytes with each
                      ;; response - there is a min-bytes threshold to
                      ;; trigger data to the client
                      (send! stream (format "%s ;; %s\n" n data) false))
                    ;; 2-arity send! closes the stream
                    (send! stream "]")))))}))))

  ;; TODO: come up with a way to make sure the data actually comes in as chunks
  (let [response (get-response "http://localhost:8080/")]
    (is (= 200 (:status response)))
    (is (= "chunked" (-> response :headers :transfer-encoding)))
    (is (= (range 10) (-> response :body read-string)))))

(deftest non-chunked-stream-servlet
  (let [data (rand-data 128)]
    (web/run
      (servlet/create-servlet
        #(as-channel %
           {:on-open
            (fn [stream]
              (send! stream data))})))

    (let [response (get-response "http://localhost:8080/")]
      (is (= 200 (:status response)))
      (is (empty? (-> response :headers :transfer-encoding)))
      (is (= (count data) (-> response :headers :content-length read-string)))
      (is (= data (:body response))))))
