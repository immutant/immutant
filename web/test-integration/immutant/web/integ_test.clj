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
            [testing.web :refer (get-body get-response event-source
                                  handle-events cookies)]))

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

(let [serializer (java.util.concurrent.Executors/newSingleThreadExecutor)]
  (defn mark [& msg]
    (when (System/getenv "VERBOSE")
      (let [ts (.format (java.text.SimpleDateFormat. "HH:mm:ss,SSS")
                 (java.util.Date.))
            ^Runnable r #(apply println ts msg)]
        (when (in-container?) (apply println ts msg))
        (.submit serializer r)))))

(defmacro marktest [t & body]
  `(deftest ~t
     (let [v# (-> ~t var meta :name)]
       (mark "START" v#)
       ~@body
       (mark "FINISH" v#))))

(defmacro flog
  "runs the body 100 times"
  [& body]
  `(if (System/getenv "FLOG")
     (dotimes [n# 100]
       (mark (format "RUNNING %s (iteration %s)"
               (-> *testing-vars* first meta :name)
               n#))
       ~@body)
     (do ~@body)))

(marktest http-session-store
  (is (= "0" (get-body (url) :cookies nil)))
  (is (= {:count 1} (decode (get-body (str (url) "session")))))
  (is (= "1" (get-body (url))))
  (is (= {:count 2} (decode (get-body (str (url) "session"))))))

(marktest session-invalidation
  (let []
    (is (= "0" (get-body (url) :cookies nil)))
    (is (= "1" (get-body (url))))
    (let [{:keys [name] :as expected} (-> @cookies first (select-keys [:name :value]))]
      (is (= "JSESSIONID" name))
      (is (empty? (get-body (str (url) "unsession"))))
      (is (= expected (-> @cookies first (select-keys [:name :value]))))
      (is (= "0" (get-body (url)))))))

(marktest share-session-with-websocket
  (is (= "0" (get-body (url) :cookies nil)))
  (is (= "1" (get-body (url))))
  (let [result (promise)]
    (with-open [socket (ws/connect (str (url "ws") "dump")
                         :on-receive (fn [s] (deliver result (decode s)))
                         :cookies @cookies)]
      (ws/send-msg socket "doesn't matter")
      (let [upgrade (deref result 5000 {})]
        (is (= {:count 2} (:session upgrade))))))
  (is (= "2" (get-body (url)))))

(marktest request-map-entries
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

;; IMMUTANT-610
(marktest test-non-decoded-path-info
  (let [request (decode (get-body (str (url) "dump/request%2B?query=help")
                                  :headers {:content-type "text/html; charset=utf-8"}))]
    (is (= (:path-info request) "/request%2B"))))

(marktest non-existent-query-string-is-nil
  (let [request (decode (get-body (str (url) "dump/request") :headers {:content-type "text/html; charset=utf-8"}))]
    (is (nil? (:query-string request)))))

(marktest upgrade-request-map-entries
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

(marktest response-charset-should-be-honored
  (doseq [charset ["UTF-8" "Shift_JIS" "ISO-8859-1" "UTF-16" "US-ASCII"]]
    (let [{:keys [headers raw-body]} (get-response (str (url) "charset?charset=" charset))]
      (is (= (read-string (headers "BodyBytes"))
            (into [] (.toByteArray raw-body)))))))

(when (in-container?)
  (marktest run-in-container-outside-of-init-should-throw
    (try
      (web/run identity)
      (is false)

      (catch IllegalStateException e
        (is (re-find #"^You can't call immutant.web/run outside" (.getMessage e)))))))

(marktest write-error-handler
  (replace-handler
    '(do
       (reset! client-state (promise))
       (import 'java.io.InputStream)
       (fn [request]
         {:status 200
          :write-error-handler (fn [ex req resp]
                                 (reset! client-state [(.getMessage ex)
                                                       (:path-info req)
                                                       (:status resp)]))
          :body (proxy [InputStream] []
                  (read [_]
                    (throw (Exception. "BOOM"))))})))
  (let [response (get-response (cdef-url))
        [ex-message path-info response-status] (read-string (get-body (str (cdef-url) "state")))]
    (is (= 500 (:status response)))
    (is (= "BOOM" ex-message))
    (is (= 200 response-status))))

(marktest write-error-handler-when-body-has-started
  (replace-handler
    '(do
       (reset! client-state (promise))
       (import 'java.io.InputStream)
       (let [counter (atom 100000)]
         (fn [request]
           {:status 200
            :write-error-handler (fn [ex req resp]
                                   (reset! client-state [(.getMessage ex)
                                                         (:path-info req)
                                                         (:status resp)]))
            :body (proxy [InputStream] []
                    (read [_]
                      (if (= 0 @counter)
                        (throw (Exception. "BOOM"))
                        (do
                          (swap! counter dec)
                          1))))}))))
  (let [response (get-response (cdef-url))
        [ex-message path-info response-status] (read-string (get-body (str (cdef-url) "state")))]
    ;; with larger responses, the body will have been partially sent,
    ;; so we can't reset the status code to 500
    (is (= 200 (:status response)))
    (is (= "BOOM" ex-message))
    (is (= 200 response-status))))

(marktest write-error-handler-from-middleware
  (replace-handler
    '(do
       (reset! client-state (promise))
       (import 'java.io.InputStream)
       (immutant.web.middleware/wrap-write-error-handling
         (fn [request]
           {:status 200
            :body (proxy [InputStream] []
                    (read [_]
                      (throw (Exception. "BOOM"))))})
         (fn [ex req resp]
           (reset! client-state [(.getMessage ex)
                                 (:path-info req)
                                 (:status resp)])))))
  (let [response (get-response (cdef-url))
        [ex-message path-info response-status] (read-string (get-body (str (cdef-url) "state")))]
    (is (= 500 (:status response)))
    (is (= "BOOM" ex-message))
    (is (= 200 response-status))))

(marktest write-error-handler-from-response-should-overwrite-from-middleware
  (replace-handler
    '(do
       (reset! client-state (promise))
       (import 'java.io.InputStream)
       (immutant.web.middleware/wrap-write-error-handling
         (fn [request]
           {:status 200
            :write-error-handler (fn [_ _ _] (reset! client-state :from-response))
            :body (proxy [InputStream] []
                    (read [_]
                      (throw (Exception. "BOOM"))))})
         (fn [_ _ _] (reset! client-state :from-middleware)))))
  (let [response (get-response (cdef-url))
        from-handler (read-string (get-body (str (cdef-url) "state")))]
    (is (= 500 (:status response)))
    (is (= :from-response from-handler))))

;; async

(marktest chunked-streaming
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

(marktest non-chunked-stream
  (let [data (apply str (repeat 128 1))]
    (let [response (get-response (str (url) "non-chunked-stream"))]
      (is (= 200 (:status response)))
      (is (empty? (-> response :headers :transfer-encoding)))
      (is (= (count data) (-> response :headers :content-length read-string)))
      (is (= data (:body response))))))

(marktest ws-at-root
  (let [result (promise)]
    (with-open [socket (ws/connect (url "ws")
                         :on-receive (fn [m] (deliver result m)))]
      (ws/send-msg socket "hello")
      (is (= "ROOThello" (deref result 5000 :failure))))))

(marktest websocket-as-channel
  ;; initialize the session
  (get-body (str (url)) :cookies nil)
  (let [result (promise)]
    (with-open [socket (ws/connect (str (url "ws") "ws")
                         :on-receive (fn [m] (deliver result m))
                         :cookies @cookies)]
      (ws/send-msg socket "hello")
      (is (= "HELLO" (deref result 5000 :failure)))
      (is (= {:count 1 :ham :sandwich} (decode (get-body (str (url) "session"))))))))

(marktest websocket-upgrade-request-can-set-headers
  (let [result (promise)]
    (with-open [socket (ws/connect (str (url "ws") "ws")
                         :on-receive (fn [m] (deliver result m))
                         :cookies @cookies)
                session (ws/session socket)]
      (ws/send-msg socket "hello")
      (is (= "HELLO" (deref result 5000 :failure)))
      (let [headers (into {} (-> session .getUpgradeResponse .getHeaders))]
        (is (= "biscuit" (first (headers "ham"))))))))

(marktest nested-ws-routes
  (doseq [path ["" "foo" "foo/bar"]]
    (let [result (promise)]
      (with-open [socket (ws/connect (format "%snested-ws/%s" (url "ws") path)
                           :on-receive (fn [m] (deliver result m)))]
        (ws/send-msg socket "whatevs")
        (is (= (str "/" path)
              (-> result (deref 5000 "nil") read-string :path-info)))))))

(marktest websocket-from-user-servlet
  (with-open [socket (ws/connect (str (url "ws") "user-defined-servlet"))]
    (ws/send-msg socket "hello"))
  (is (= [:open "hello" 1001] (read-string (get-body (str (url) "user-defined-servlet?get-result"))))))

(marktest stream-from-user-servlet
  (with-open [client (http/create-client)]
    (let [response (http/stream-seq client :get (str (url) "user-defined-servlet"))
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
      (is (= 10 (count @body))))))

(marktest websocket-from-wrapped-handler-servlet
  (with-open [socket (ws/connect (str (url "ws") "wrapped-handler-servlet"))]
    (ws/send-msg socket "hello"))
  (is (= [:open "hello" 1001] (read-string (get-body (str (url) "wrapped-handler-servlet?get-result"))))))

(marktest stream-from-wrapped-handler-servlet
  (with-open [client (http/create-client)]
    (let [response (http/stream-seq client :get (str (url) "wrapped-handler-servlet"))
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
      (is (= 10 (count @body))))))

(marktest concurrent-ws-requests-should-not-cross-streams
  (replace-handler
    '(fn [request]
       (async/as-channel request
         :on-open (fn [ch]
                    (let [query-string (:query-string (async/originating-request ch))]
                      (async/send! ch (last (re-find #"x=(.*)$" query-string))))))))
  (let [results (atom [])
        clients (atom [])
        done? (promise)
        client-count 40]
    (dotimes [n client-count]
      (future
        ;;(mark "CLIENT SEND" n)
        (let [client (ws/connect (str (cdef-url "ws") "?x=" n)
                       :on-receive (fn [m]
                                     ;;(mark "CLIENT RCVD" m)
                                     (swap! results conj m)
                                     (when (= client-count (count @results))
                                       (deliver done? true))))]
          (swap! clients conj client))))
    (is (deref done? 10000 nil))
    ;;(mark "RESULTS" @results)
    (is (= (->> client-count (range 0) (map str) set)
          (set @results)))
    (doseq [client @clients]
      (.close client))))

(marktest request-should-be-attached-to-channel-for-ws
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

(marktest ws-on-close-should-be-invoked-when-closing-on-server-side
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

(marktest ws-on-close-should-be-invoked-when-closing-on-client
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-close (fn [_ r] (deliver @client-state r))))))
  (let [socket (ws/connect (cdef-url "ws"))]
    (ws/close socket)
    (is (= 1001 (:code (read-string (get-body (str (cdef-url) "state"))))))))

(marktest ws-on-close-should-be-invoked-for-every-connection
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

(marktest ws-on-success-should-be-called-after-send
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

(marktest ws-on-error-is-called-if-on-success-throws
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

(marktest ws-send!-nil-should-work
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

(marktest request-should-be-attached-to-channel-for-stream
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

(marktest send!-to-stream-with-map-overrides-status-headers
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

(marktest send!-to-stream-with-map-after-send-has-started-throws
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

(marktest send!-to-ws-with-map-throws
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

(marktest closing-a-stream-with-no-send-should-honor-original-response
  (replace-handler
    '(fn [request]
       (assoc (async/as-channel request
                :on-open async/close)
         :status 201
         :headers {"ham" "biscuit"})))
  (let [{:keys [status headers]} (get-response (cdef-url))]
    (is (= 201 status))
    (is (= "biscuit" (:ham headers)))))

(marktest stream-on-close-should-be-invoked-when-closing-on-server-side
  (replace-handler
    '(do
       (reset! client-state (promise))
       (fn [request]
         (async/as-channel request
           :on-open (fn [ch] (async/close ch))
           :on-close (fn [_ r] (deliver @client-state :closed))))))
  (is (= nil (get-body (cdef-url))))
  (is (= :closed (read-string (get-body (str (cdef-url) "state"))))))

(marktest nil-send!-to-stream-should-work
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

(marktest stream-on-success-should-be-called-after-send
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

(marktest stream-on-error-is-called-if-on-success-throws
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

(marktest send!-a-string
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch "biscuit"
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-a-string")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-a-string")
                           (.printStackTrace e)))))]
    (flog
      (replace-handler handler)
      (is (= "biscuit" (get-body (cdef-url))))
      (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

      (replace-handler handler)
      (let [result (promise)]
        (with-open [socket (ws/connect (cdef-url "ws")
                             :on-receive (fn [m]
                                           (deliver result m)))]
          (is (= "biscuit" (deref result 5000 nil)))))
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(marktest send!-a-byte-array
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch (.getBytes "biscuit")
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-a-byte-array")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-a-byte-array")
                           (.printStackTrace e)))))]
    (flog
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
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(marktest send!-a-sequence
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch (list "ham" (.getBytes "biscuit") (list "gravy"))
                            {:close? true
                             :on-success #(deliver @client-state :complete!)
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-a-sequence")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-a-sequence")
                           (.printStackTrace e)))))]
    (flog
      (replace-handler handler)
      (is (= "hambiscuitgravy" (String. (get-body (cdef-url)))))
      (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

      (replace-handler handler)
      (let [done? (promise)
            results (atom [])]
        (with-open [socket (ws/connect (cdef-url "ws")
                             :on-receive (fn [m]
                                           (swap! results conj m)
                                           (when (= 3 (count @results))
                                             (deliver done? true)))
                             :on-binary (fn [m _ _]
                                          (swap! results conj m)))]
          (is (deref done? 10000 nil))
          (let [[h b g] @results]
            (is (= ["ham" (into [] (.getBytes "biscuit")) "gravy"]
                  [h (into [] b) g])))))
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(marktest send!-an-empty-sequence
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :on-open (fn [ch]
                          (async/send! ch '()
                            {:close? true
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-an-empty-sequence")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-close (fn [_ _] (deliver @client-state :complete!))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-an-empty-sequence")
                           (.printStackTrace e)))))]
    (flog
      (replace-handler handler)
      (get-body (cdef-url))
      (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))

      (replace-handler handler)
      (with-open [socket (ws/connect (cdef-url "ws"))]
        (is (= :complete! (read-string (get-body (str (cdef-url) "state")))))))))

(marktest send!-a-file
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
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-a-file")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-a-file")
                           (.printStackTrace e)))))]
    (flog
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
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(marktest send!-with-input-stream-larger-than-size-hint
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
                             :on-error (fn [e]
                                         (println "SEND ERROR send!-with-input-stream-larger-than-size-hint")
                                         (.printStackTrace e)
                                         (deliver @client-state e))}))
               :on-error (fn [_ e]
                           (println "CHANNEL ERROR send!-with-input-stream-larger-than-size-hint")
                           (.printStackTrace e)))))
        data (->> "data"
               io/resource io/file slurp)]
    (flog
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
      (is (= :complete! (read-string (get-body (str (cdef-url) "state"))))))))

(marktest ws-should-timeout-when-idle
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :timeout 100
               :on-error (fn [_ e]
                           (println "ERROR ON ws-should-timeout-when-idle")
                           (.printStackTrace e))
               :on-open (fn [ch]
                          (async/send! ch "open")
                          (println "OPENED ws-should-timeout-when-idle"))
               :on-close (fn [_ _]
                           (println "CLOSING ws-should-timeout-when-idle")
                           (deliver @client-state :closed)))))
        ready (promise)]
    (replace-handler handler)
    (with-open [socket (ws/connect (cdef-url "ws")
                         :on-receive (fn [m]
                                       (deliver ready m)))]
      (is (= "open" (deref ready 1000 :failure)))
      (is (= :closed (read-string (get-body (str (cdef-url) "state"))))))))

(marktest ws-timeout-should-occur-when-truly-idle
  (let [handler
        '(do
           (reset! client-state (promise))
           (fn [request]
             (async/as-channel request
               :timeout 100
               :on-error (fn [_ e]
                           (println "ERROR ON ws-timeout-should-occur-when-truly-idle")
                           (.printStackTrace e))
               :on-open (fn [ch]
                          (future
                            (dotimes [n 4]
                              (async/send! ch (str n))
                              (Thread/sleep 50))
                            (async/send! ch "done")
                            (println "OPENED ws-timeout-should-occur-when-truly-idle")))
               :on-close (fn [_ reason]
                           (println "CLOSING ws-timeout-should-occur-when-truly-idle")
                           (deliver @client-state :closed)))))
        ready (promise)
        data (atom [])]
    (replace-handler handler)
    (with-open [socket (ws/connect (cdef-url "ws")
                         :on-receive (fn [m]
                                       (if (= "done" m)
                                         (deliver ready m)
                                         (swap! data conj m))))]
      (is (= "done" (deref ready 1000 :failure)))
      (is (= ["0" "1" "2" "3"] @data))
      (Thread/sleep 150)
      (is (= :closed (read-string (get-body (str (cdef-url) "state"))))))))

(marktest stream-timeouts
  (if (or (immutant.web.internal.servlet/async-streaming-supported?)
        (not (in-container?)))
    (let [handler
          '(do
             (reset! client-state (promise))
             (fn [request]
               (async/as-channel request
                 :timeout 100
                 :on-error (fn [_ e]
                             (println "ERROR stream-timeouts")
                             (.printStackTrace e))
                 :on-close (fn [_ reason]
                             (println "CLOSE stream-timeouts")
                             (deliver @client-state :closed)))))]
      (flog
        (replace-handler handler)
        (is (= 200 (:status (get-response (cdef-url)))))
        (is (= :closed (read-string (get-body (str (cdef-url) "state")))))))
    ;; in WF 8.2, a timeout causes a deadlock in ispan around the
    ;; session, so we don't allow timeouts there
    (let [handler
          '(do
             (reset! client-state (promise))
             (fn [request]
               (try
                 (async/as-channel request
                   :timeout 100)
                 (catch IllegalArgumentException e
                   (deliver @client-state (.getMessage e))
                   {:status 500}))))]
      (replace-handler handler)
      (is (= 500 (:status (get-response (cdef-url)))))
      (is (re-find #"HTTP stream timeouts are not supported on this platform"
            (read-string (get-body (str (cdef-url) "state"))))))))

;; TODO: build a long-running random test

(when (not (in-container?))
  ;; TODO: Run this in-container. The only thing stopping us is our
  ;; jersey sse client seems to be incompatible with resteasy
  (marktest server-sent-events
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
