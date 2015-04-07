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
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [gniazdo.core :as ws]
            [http.async.client :as http]
            [immutant.codecs :refer (decode)]
            [immutant.internal.util :refer [try-resolve]]
            [immutant.util :refer (in-container? http-port)]
            [immutant.web :refer (stop) :as web]
            [testing.app :refer (run)]
            [testing.web :refer (get-body get-response event-source handle-events)]))

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
                socket (http/websocket client (str (url "ws") "dump")
                         :text (fn [_ s] (deliver result (decode s)))
                         :cookies @testing.web/cookies)]
      (http/send socket :text "doesn't matter")
      (let [upgrade (deref result 5000 {})]
        (is (= {:count 2} (:session upgrade))))))
  (is (= "2" (get-body (url)))))

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
    (with-open [s (ws/connect (str (url "ws") "dump?x=y&j=k")
                    :on-receive (fn [m] (deliver request (decode m))))]
      (let [m (deref request 5000 :failure)]
        (are [x expected] (= expected (x m))
             :websocket?          true
             :uri                 (str (if (in-container?) "/integs") "/dump")
             :context             (str (if (in-container?) "/integs") "/dump")
             :path-info           "/"
             :query-string        "x=y&j=k"
             :scheme              :http
             :request-method      :get
             :server-port         (http-port)
             :server-name         "localhost")
        (is (= "Upgrade" (get-in m [:headers "connection"])))
        (is (= "k" (get-in m [:params "j"])))))))

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
  (let [data (apply str (repeat 128 1))]
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

(deftest nested-ws-routes
  (doseq [path ["" "foo" "foo/bar"]]
    (let [result (promise)]
      (with-open [socket (ws/connect (format "%snested-ws/%s" (url "ws") path)
                           :on-receive (fn [m] (deliver result m)))]
        (ws/send-msg socket "whatevs")
        (is (= (str "/" path)
              (-> result (deref 5000 "nil") read-string :path-info)))))))


(deftest request-should-be-attached-to-channel-for-ws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-message (fn [ch _] (deliver @client-state
                                   (dissoc (async/originating-request ch)
                                     :body
                                     :server-exchange
                                     :servlet
                                     :servlet-context
                                     :servlet-request
                                     :servlet-response)))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (let [request (read-string (get-body (str (cdef-url) "state")))]
      (is request)
      (is (= "/" (:path-info request)))
      (is (= "Upgrade" (get-in request [:headers "connection"]))))))

(deftest ws-on-close-should-be-invoked-when-closing-on-server-side
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

(deftest ws-on-close-should-be-invoked-when-closing-on-client
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-close (fn [_ r] (deliver @client-state r))))))
  (let [socket (ws/connect (cdef-url "ws"))]
    (ws/close socket)
    (is (= 1000 (:code (read-string (get-body (str (cdef-url) "state"))))))))

(deftest ws-on-close-should-be-invoked-for-every-connection
  (replace-handler
    '(do
       (reset! client-state #{})
       (fn [request]
         (async/as-channel request
           :on-close (fn [ch r] (swap! client-state conj (str ch)))))))
  (let [socket1 (ws/connect (cdef-url "ws"))
        socket2 (ws/connect (cdef-url "ws"))]
    (ws/close socket1)
    (ws/close socket2)
    (is (= 2 (-> (str (cdef-url) "state") get-body read-string count)))))

(deftest ws-on-success-should-be-called-after-send
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-message (fn [ch m]
                         (async/send! ch m
                           {:on-success (fn []
                                           (deliver @client-state :complete!))}))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest ws-on-error-is-called-if-on-success-throws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-error (fn [_ err] (deliver @client-state (.getMessage err)))
           :on-message (fn [ch m]
                         (async/send! ch m
                           {:on-success (fn [] (throw (Exception. "BOOM")))}))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= "BOOM" (read-string (get-body (str (cdef-url) "state")))))))

(deftest ws-send!-nil-should-work
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-message (fn [ch _]
                         (async/send! ch nil
                           {:on-success #(deliver @client-state :complete!)
                            :on-error (partial deliver @client-state)}))))))
  (with-open [socket (ws/connect (cdef-url "ws"))]
    (ws/send-msg socket "hello")
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest request-should-be-attached-to-channel-for-stream
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch]
                      (deliver @client-state
                        (dissoc (async/originating-request ch)
                          :body
                          :server-exchange
                          :servlet
                          :servlet-context
                          :servlet-request
                          :servlet-response))
                      (async/send! ch "done" :close? true))))))
  (is (= "done" (get-body (cdef-url))))
  (let [request (read-string (get-body (str (cdef-url) "state")))]
    (is request)
    (is (= "/" (:path-info request)))))

(deftest send!-to-stream-with-map-overrides-status-headers
  (replace-handler
    '(fn [request]
       (async/as-channel request
         :on-open (fn [ch]
                    (async/send! ch {:body "ahoy"
                                     :status 201
                                     :headers {"foo" "bar"}}
                      :close? true)))))
  (let [{:keys [body headers status]} (get-response (cdef-url))]
    (is (= "ahoy" body))
    (is (= 201 status))
    (is (= "bar" (:foo headers)))))

(deftest send!-to-stream-with-map-after-send-has-started-throws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch]
                      (async/send! ch "opening-")
                      (try
                        (async/send! ch {:body "ahoy"})
                        (catch Exception e
                          (deliver @client-state (.getMessage e))))
                      (async/send! ch "closing" :close? true))))))
  (is (= "opening-closing" (get-body (cdef-url))))
  (is (re-find #"this is not the first send"
        (read-string (get-body (str (cdef-url) "state"))))))

(deftest send!-to-ws-with-map-throws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch]
                      (try
                        (async/send! ch {:body "ahoy"})
                        (catch Exception e
                          (deliver @client-state (.getMessage e)))))))))
  (.close (ws/connect (cdef-url "ws")))
  (is (re-find #"channel is not an HTTP stream channel"
        (read-string (get-body (str (cdef-url) "state"))))))

(deftest closing-a-stream-with-no-send-should-honor-original-response
  (replace-handler
    '(fn [request]
       (assoc (async/as-channel request
                :on-open async/close)
         :status 201
         :headers {"ham" "biscuit"})))
  (let [{:keys [status headers]} (get-response (cdef-url))]
    (is (= 201 status))
    (is (= "biscuit" (:ham headers)))))

(deftest stream-on-close-should-be-invoked-when-closing-on-server-side
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch] (async/close ch))
           :on-close (fn [_ r] (deliver @client-state :closed))))))
  (is (= nil (get-body (cdef-url))))
  (is (= :closed (read-string (get-body (str (cdef-url) "state"))))))

(deftest nil-send!-to-stream-should-work
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch]
                      (async/send! ch nil
                        {:close? true}))
           :on-close (fn [_ r] (deliver @client-state :closed))))))
  (let [{:keys [status body]} (get-response (cdef-url))]
    (is (= 200 status))
    (is (nil? body))
    (is (= :closed (read-string (get-body (str (cdef-url) "state")))))))

(deftest stream-on-success-should-be-called-after-send
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch]
                      (async/send! ch "ahoy"
                        {:close? true
                         :on-success #(deliver @client-state :complete!)}))))))
  (is (= "ahoy" (get-body (cdef-url))))
  (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))

(deftest stream-on-error-is-called-if-on-success-throws
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-error (fn [_ err] (deliver @client-state (.getMessage err)))
           :on-open (fn [ch]
                      (async/send! ch "ahoy"
                        {:close? true
                         :on-success #(throw (Exception. "BOOM"))}))))))
  (is (= "ahoy" (get-body (cdef-url))))
  (is (= "BOOM" (read-string (get-body (str (cdef-url) "state"))))))

(deftest send!-a-string
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch "biscuit"
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error #(deliver @client-state %)})))))]
    (replace-handler handler)
    (is (= "biscuit" (get-body (cdef-url))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (let [result (promise)]
      (with-open [socket (ws/connect (cdef-url "ws")
                           :on-receive (fn [m]
                                         (deliver result m)))]
        (is (= "biscuit" (deref result 5000 nil)))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest send!-a-byte-array
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch (.getBytes "biscuit")
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error #(deliver @client-state %)})))))]
    (replace-handler handler)
    (is (= "biscuit" (String. (get-body (cdef-url)))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (let [result (promise)]
      (with-open [socket (ws/connect (cdef-url "ws")
                           :on-binary (fn [m _ _]
                                        (deliver result m)))]
        (is (= (into [] (.getBytes "biscuit"))
              (into [] (deref result 5000 nil))))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest send!-a-sequence
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch (list "ham" (.getBytes "biscuit") (list "gravy"))
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error #(deliver @client-state %)})))))]
    (replace-handler handler)
    (is (= "hambiscuitgravy" (String. (get-body (cdef-url)))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (let [done? (promise)
          results (atom [])]
      (with-open [socket (ws/connect (cdef-url "ws")
                           :on-receive (partial swap! results conj)
                           :on-binary (fn [m _ _]
                                        (swap! results conj m)
                                        (deliver done? true)))]
        (is (deref done? 5000 nil))
        (let [[h b g] @results]
          (is (= ["ham" (into [] (.getBytes "biscuit")) "gravy"]
                [h (into [] b) g])))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest send!-an-empty-sequence
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch '()
                            {:close? true}))
               :on-close (fn [_ _] (deliver @client-state :complete!)))))]
    (replace-handler handler)
    (get-body (cdef-url))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (with-open [socket (ws/connect (cdef-url "ws"))]
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(deftest send!-a-file
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-error (fn [_ e] (.printStackTrace e))
               :on-open (fn [ch]
                          (async/send! ch (io/file (io/resource "public/foo.html"))
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error #(deliver @client-state %)})))))]
    (replace-handler handler)
    (is (= (slurp (io/file (io/resource "public/foo.html")))
          (String. (get-body (cdef-url)))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (let [result (promise)]
      (with-open [socket (ws/connect (cdef-url "ws")
                           :on-binary (fn [m _ _]
                                        (deliver result m)))]
        (is (= (into [] (-> (io/resource "public/foo.html")
                          io/file slurp .getBytes))
              (into [] (deref result 5000 nil))))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

(deftest send!-with-input-stream-larger-than-size-hint
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-error (fn [_ e] (.printStackTrace e))
               :on-open (fn [ch]
                          (async/send! ch (-> "data"
                                            io/resource io/file)
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error #(deliver @client-state %)})))))
        data (->> "data"
               io/resource io/file slurp)]
    (replace-handler handler)
    (is (= data (get-body (cdef-url))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

    (replace-handler handler)
    (let [done? (promise)
          rcvd (atom "")]
      (with-open [socket (ws/connect (cdef-url "ws")
                           :on-binary (fn [m _ _]
                                        (when (= (+ 2 (* 16 1024))
                                                (count (swap! rcvd #(str % (String. m)))))
                                          (deliver done? true))))]
        (is (deref done? 5000 nil))
        (is (= data @rcvd))))
    (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))

;; TODO: build a long-running random test

(when (not (in-container?))
  ;; TODO: Run this in-container. The only thing stopping us is our
  ;; jersey sse client seems to be incompatible with resteasy
  (deftest server-sent-events
    (let [closed (promise)
          result (atom [])
          client (as-> (event-source (str (url) "sse")) c
                   (handle-events c (fn [e]
                                      (swap! result conj (.readData e))
                                      (when (= "close" (.getName e))
                                        (.close c)
                                        (deliver closed :success)))))]
      (.open client)
      (is (= :success (deref closed 5000 :fail)))
      (is (= ["5" "4" "3" "2" "1" "bye!"] @result))
      (is (not (.isOpen client))))))
