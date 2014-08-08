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
            [immutant.web.middleware :refer [wrap-session]]
            [ring.util.response :refer [response]]
            [http.async.client :as http]
            [testing.web :refer [hello get-body]]
            [gniazdo.core :as ws]
            [clojure.string :refer [upper-case]])
  (:import [io.undertow.util Sessions]))

(def url "http://localhost:8080/")

(defn test-websocket
  [create-handler]
  (let [path "/test"
        events (atom [])
        result (promise)
        handler (create-handler
                  {:on-open    (fn [& _]
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

;; (deftest jsr-356-websocket
;;   (let [expected [:open "hello" 1000]]
;;     (is (= expected (test-websocket (comp (partial attach-endpoint (create-servlet hello)) create-endpoint))))))

(deftest middleware-websocket
  (let [expected [:open "hello" 1000]]
    (is (= expected (test-websocket (partial wrap-websocket hello))))))

(deftest remote-sending-to-client-using-gniazdo
  (let [result (promise)
        server (run (wrap-websocket nil {:on-message (fn [c m] (send! c (upper-case m)))}))
        socket (ws/connect "ws://localhost:8080" :on-receive #(deliver result %))]
    (try
      (ws/send-msg socket "hello")
      (is (= "HELLO" (deref result 2000 "goodbye")))
      (finally
        (ws/close socket)
        (stop server)))))

(deftest remote-sending-to-client-using-httpasyncclient
  (let [result (promise)
        server (run (wrap-websocket nil {:on-message (fn [c m] (send! c (upper-case m)))}))]
    (try
      (with-open [client (http/create-client)
                  socket (http/websocket client "ws://localhost:8080"
                           :text (fn [_ m] (deliver result m)))]
        (http/send socket :text "hello")
        (is (= "HELLO" (deref result 2000 "goodbye"))))
      (finally
        (stop server)))))

(deftest handshake-headers
  (let [result (promise)
        endpoint (wrap-websocket nil :on-open (fn [ch hs] (deliver result hs)))]
    (run endpoint)
    (with-open [client (http/create-client)
                socket (http/websocket client "ws://localhost:8080/?x=y&j=k")]
      (let [handshake (deref result 1000 nil)]
        (is (not (nil? handshake)))
        (is (= "Upgrade"   (-> handshake headers (get "Connection") first)))
        (is (= "k"         (-> handshake parameters (get "j") first)))
        (is (= "x=y&j=k"   (-> handshake query-string)))
        ;; TODO: bug in undertow! (is (= "/?x=y&j=k" (-> handshake uri str)))
        (is (false?        (-> handshake (user-in-role? "admin"))))))
    (stop)))

(deftest share-session-with-websocket
  (let [result (promise)
        handler (fn [{{:keys [id] :or {id (str (rand))}} :session}]
                  (-> id response (assoc :session {:id id})))]
    (run (wrap-websocket (wrap-session handler)
           :on-open (fn [ch hs] (deliver result (:id (session hs))))))
    ;; establish the id in the session with the first request
    (let [id (get-body url :cookies nil)]
      ;; make sure we get it again if we pass the returned cookie
      (is (= id (get-body url)))
      ;; now open a websocket connection with the same cookie
      (with-open [client (http/create-client)
                  socket (http/websocket client "ws://localhost:8080"
                           :cookies @testing.web/cookies)]
        ;; and verify the websocket sees the same id
        (is (= id (deref result 1000 :failure)))))
    (stop)))

(deftest session-invalidation
  (let [http (atom {})
        http-session (fn [r] (-> r :server-exchange Sessions/getOrCreateSession))
        handler (fn [req]
                  (reset! http (http-session req))
                  (if-not (-> req :session :foo)
                    (-> (response "yay")
                      (assoc :session {:foo "yay"}))
                    (-> (response "boo")
                      (assoc :session nil))))]
    (run (wrap-session handler))
    (is (= "yay" (get-body url :cookies nil)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "boo" (get-body url)))
    (is (thrown? IllegalStateException (-> @http (.getAttribute "ring-session-data") :foo)))
    (is (= "yay" (get-body url)))
    (is (= "yay" (-> @http (.getAttribute "ring-session-data") :foo)))
    (stop)))
