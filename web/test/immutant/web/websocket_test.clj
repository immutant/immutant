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

(ns immutant.web.websocket-test
  (:require [clojure.test :refer :all]
            [immutant.web :refer :all]
            [immutant.web.websocket :refer :all]
            [immutant.web.javax :as javax]
            [gniazdo.core :as ws]
            [clojure.string :refer [upper-case]]))

(deftest happy-native-undertow
  (try
    (let [events (atom [])
          result (promise)
          handler (create-handler
                    :on-open    (fn [_]
                                  (swap! events conj :open))
                    :on-close   (fn [_ {c :code}]
                                  (deliver result (swap! events conj c)))
                    :on-message (fn [_ m]
                                  (swap! events conj m)))]
      (mount (server) handler)
      (let [socket (ws/connect "ws://localhost:8080/")]
        (ws/send-msg socket "hello")
        (ws/close socket))
      (is (= [:open "hello" 1000] (deref result 2000 :fail))))
    (finally
      (unmount))))

(deftest happy-servlet
  (mount-servlet (server) (javax/create-endpoint-servlet {:on-message #(send! %1 (upper-case %2))}))
  (let [socket (ws/connect "ws://localhost:8080/"
                 :on-receive #(prn 'received %))]
    (try
      (ws/send-msg socket "hello")
      (finally
        (ws/close socket)
        (unmount)))))
