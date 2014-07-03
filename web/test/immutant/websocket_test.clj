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

(ns immutant.websocket-test
  (:require [clojure.test :refer :all]
            [immutant.web :refer :all]
            [immutant.web.javax :refer (create-servlet)]
            [immutant.websocket :refer :all]
            [http.async.client :as http]
            [testing.web  :refer [hello]]
            [gniazdo.core :as ws]
            [clojure.string :refer [upper-case]]))

(defn test-websocket
  [create-handler]
  (let [path "/test"
        events (atom [])
        result (promise)
        handler (create-handler
                  {:on-open    (fn [_]
                                 (swap! events conj :open))
                   :on-close   (fn [_ {c :code}]
                                 (deliver result (swap! events conj c)))
                   :on-message (fn [_ m]
                                 (swap! events conj m))})]
    (try
      (run handler {:path path})
      (let [socket (ws/connect (str "ws://localhost:8080" path))]
        (ws/send-msg socket "hello")
        (ws/close socket))
      (deref result 2000 :fail)
      (finally
        (stop {:path path})))))

(deftest undertow-websocket
  (let [expected [:open "hello" 1000]]
    (is (= expected (test-websocket create-handler)))))

(deftest jsr-356-websocket
  (let [expected [:open "hello" 1000]]
    (is (= expected (test-websocket (partial attach-endpoint (create-servlet hello)))))))

(deftest remote-sending-to-client-using-gniazdo
  (let [result (promise)
        server (run (create-handler {:on-message (fn [c m] (send! c (upper-case m)))}))
        socket (ws/connect "ws://localhost:8080" :on-receive #(deliver result %))]
    (try
      (ws/send-msg socket "hello")
      (is (= "HELLO" (deref result 2000 "goodbye")))
      (finally
        (ws/close socket)
        (stop server)))))

(deftest remote-sending-to-client-using-httpasyncclient
  (let [result (promise)
        server (run (create-handler {:on-message (fn [c m] (send! c (upper-case m)))}))]
    (try
      (with-open [client (http/create-client)
                  socket (http/websocket client "ws://localhost:8080"
                           :text (fn [_ m] (deliver result m)))]
        (http/send socket :text "hello")
        (is (= "HELLO" (deref result 2000 "goodbye"))))
      (finally
        (stop server)))))
