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

(ns immutant.web.integ-test
  (:require [clojure.test :refer :all]
            [testing.web :refer (get-body)]
            [testing.app :refer (run)]
            [immutant.web :refer (stop)]
            [immutant.codecs :refer (decode)]
            [immutant.util :refer (in-container? http-port)]
            [http.async.client :as http]))

(when-not (in-container?)
  (use-fixtures :once
    (fn [f]
      (let [server (run)]
        (try (f) (finally (stop server)))))))

(defn url
  [& [scheme]]
  (format "%s://localhost:%s"
    (or scheme "http")
    (if (in-container?)
      (format "%s/integs/" (http-port))
      "8080/")))

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

        ;; TODO: this is a bug in undertow, fixed in next release
        ;; https://github.com/undertow-io/undertow/pull/236
        (if (in-container?)
          (is (.endsWith (:uri handshake) "/?x=y&j=k"))
          (is (= "/x=y&j=k" (:uri handshake))))))))

(deftest request-map-entries
  (let [request (decode (get-body (str (url) "request?query=help") :headers {:content-type "text/html; charset=utf-8"}))]
    (are [x expected] (= expected (x request))
         :content-type        "text/html; charset=utf-8"
         :character-encoding  "utf-8"
         :remote-addr         "127.0.0.1"
         :server-port         (http-port)
         :content-length      -1
         :uri                 (str (:context request) "/request")
         :server-name         "localhost"
         :query-string        "query=help"
         :scheme              :http
         :request-method      :get)
    (is (-> request :session :count))
    (is (map? (:headers request)))
    (is (< 3 (count (:headers request))))))
