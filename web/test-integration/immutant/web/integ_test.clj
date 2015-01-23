;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.integ-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [http.async.client :as http]
            [gniazdo.core :as ws]
            [immutant.codecs :refer (decode)]
            [immutant.internal.util :refer [try-resolve]]
            [immutant.util :refer (in-container? http-port)]
            [immutant.web :refer (stop) :as web]
            [testing.app :refer (run)]
            [testing.web :refer (get-body get-response)]))

(when-not (in-container?)
  (use-fixtures :once
    (fn [f]
      (let [server (run)]
        (try (f) (finally (stop server)))))))

(defn url
  ([]
     (url "http"))
  ([protocol]
     (if (in-container?)
       (str ((try-resolve 'immutant.wildfly/base-uri) "localhost" protocol) "/")
       (format "%s://localhost:8080/" protocol))))

(defn cdef-url
  ([]
   (cdef-url "http"))
  ([proto]
   (str (url proto) "cdef/")))

(defn replace-handler [form]
  (get-response (str (cdef-url) "set-handler")
    :query {:new-handler (pr-str form)}))

(deftest http-session-store
  (is (= "0" (get-body (url) :cookies nil)))
  (is (= {:count 1} (decode (get-body (str (url) "session")))))
  (is (= "1" (get-body (url))))
  (is (= {:count 2} (decode (get-body (str (url) "session"))))))

(deftest session-invalidation
  (let []
    (is (= "0" (get-body (url) :cookies nil)))
    (is (= "1" (get-body (url))))
    (let [{:keys [name] :as expected} (-> @testing.web/cookies first (select-keys [:name :value]))]
      (is (= "JSESSIONID" name))
      (is (empty? (get-body (str (url) "unsession"))))
      (is (= expected (-> @testing.web/cookies first (select-keys [:name :value]))))
      (is (= "0" (get-body (url)))))))

(deftest share-session-with-websocket
  (is (= "0" (get-body (url) :cookies nil)))
  (is (= "1" (get-body (url))))
  (let [result (promise)]
    (with-open [client (http/create-client)
                socket (http/websocket client (url "ws")
                         :text (fn [_ s] (deliver result (decode s)))
                         :cookies @testing.web/cookies)]
      (http/send socket :text "doesn't matter")
      (let [handshake (deref result 5000 {})]
        (is (= {:count 2} (:session handshake))))))
  (is (= "2" (get-body (url)))))

(deftest handshake-headers
  (let [result (promise)]
    (with-open [client (http/create-client)
                socket (http/websocket client (str (url "ws") "?x=y&j=k")
                         :text (fn [_ s] (deliver result (decode s))))]
      (http/send socket :text "doesn't matter")
      (let [handshake (deref result 5000 nil)]
        (is (not (nil? handshake)))
        (is (= "Upgrade"   (-> handshake :headers (get "Connection") first)))
        (is (= "k"         (-> handshake :parameters (get "j") first)))
        (is (= "x=y&j=k"   (-> handshake :query)))
        (.endsWith (:uri handshake) "/?x=y&j=k")))))

(deftest request-map-entries
  (get-body (url)) ;; ensure there's something in the session
  (let [request (decode (get-body (str (url) "dump/request?query=help") :headers {:content-type "text/html; charset=utf-8"}))]
    (are [x expected] (= expected (x request))
         :content-type        "text/html; charset=utf-8"
         :character-encoding  "utf-8"
         :remote-addr         "127.0.0.1"
         :server-port         (http-port)
         :content-length      -1
         :uri                 (str (if (in-container?) "/integs") "/dump/request")
         :path-info           "/request"
         :context             (str (if (in-container?) "/integs") "/dump")
         :server-name         "localhost"
         :query-string        "query=help"
         :scheme              :http
         :request-method      :get)
    (is (-> request :session :count))
    (is (map? (:headers request)))
    (is (< 3 (count (:headers request))))))

(deftest upgrade-request-map-entries
  (let [request (promise)]
    (with-open [c (http/create-client)
                s (http/websocket c (str (url "ws") "dump")
                    :text (fn [_ m] (deliver request (decode m))))]
      (are [x expected] (= expected (x (deref request 5000 :failure)))
           :websocket?          true
           :uri                 (str (if (in-container?) "/integs") "/dump")
           :context             (str (if (in-container?) "/integs") "/dump")
           :path-info           "/"
           :scheme              :http
           :request-method      :get
           :server-port         (http-port)
           :server-name         "localhost"))))

(deftest response-charset-should-be-honored
  (doseq [charset ["UTF-8" "Shift_JIS" "ISO-8859-1" "UTF-16" "US-ASCII"]]
    (let [{:keys [headers raw-body]} (get-response (str (url) "charset?charset=" charset))]
      (is (= (read-string (headers "BodyBytes"))
            (into [] (.toByteArray raw-body)))))))

(when (in-container?)
  (deftest run-in-container-outside-of-init-should-throw
    (try
      (web/run identity)
      (is false)
      (catch IllegalStateException e
        (is (re-find #"^You can't call immutant.web/run outside" (.getMessage e)))))))

;; async

(deftest chunked-streaming
  (with-open [client (http/create-client)]
    (let [response (http/stream-seq client :get (str (url) "chunked-stream"))
          stream @(:body response)
          headers @(:headers response)
          body (atom [])]
      (loop []
        (let [v (.poll stream)]
          (when (not= v :http.async.client/done)
            (when v
              (swap! body conj (read-string (.toString v "UTF-8"))))
            (recur))))
      (is (= 200 (-> response :status deref :code)))
      (is (= "chunked" (:transfer-encoding headers)))
      (is (= (range 10) @body))
      (is (= 10 (count @body)))
      (is (= "biscuit" (:ham headers))))))

(deftest non-chunked-stream
  (let [data (str (repeat 128 "1"))]
    (let [response (get-response (str (url) "non-chunked-stream"))]
      (is (= 200 (:status response)))
      (is (empty? (-> response :headers :transfer-encoding)))
      (is (= (count data) (-> response :headers :content-length read-string)))
      (is (= data (:body response))))))

(deftest websocket-as-channel
  ;; initialize the session
  (get-body (str (url)) :cookies nil)
  (let [result (promise)]
    (with-open [client (http/create-client)
                socket (http/websocket client (str (url "ws") "ws")
                         :text (fn [_ m] (deliver result m))
                         :cookies @testing.web/cookies)]
      (http/send socket :text "hello")
      (is (= "HELLO" (deref result 5000 :failure)))
      (is (= {:count 1 :ham :sandwich} (decode (get-body (str (url) "session"))))))))

(deftest on-close-should-be-invoked-when-closing-on-server-side
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-message (fn [ch m] (async/close ch))
           :on-close (fn [_ r] (deliver @client-state r))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= 1000 (:code (read-string (get-body (str (cdef-url) "state"))))))))

(deftest on-complete-should-be-called-after-send
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-message (fn [ch m]
                         (async/send! ch m
                           :on-complete (fn [_]
                                          (deliver @client-state :complete!))))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest on-error-is-called-if-on-complete-throws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-error (fn [_ err] (deliver @client-state (.getMessage err)))
           :on-message (fn [ch m]
                         (async/send! ch m
                           :on-complete (fn [_] (throw (Exception. "BOOM")))))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= "BOOM" (read-string (get-body (str (cdef-url) "state")))))))

;; TODO: replicate the above tests for streams
