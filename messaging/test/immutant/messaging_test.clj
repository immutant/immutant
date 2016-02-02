;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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

(ns immutant.messaging-test
  (:require [clojure.test :refer :all]
            [immutant.messaging :refer :all]
            [immutant.util :as u])
  (:import org.projectodd.wunderboss.messaging.Context))

(u/set-log-level! (or (System/getenv "LOG_LEVEL") :OFF))

(def test-user "testuser")
(def test-password "testuser1!")

(use-fixtures :once u/reset-fixture)

(defn random-queue []
  (queue (str (java.util.UUID/randomUUID)) :durable? false))

(deftest queue-topic-should-work
  (is (queue "foo"))
  (is (topic "bar")))

(deftest queue-should-accept-kwargs
  (is (queue "babar" :durable? true)))

(deftest queue-should-validate-opts
  (is (thrown? IllegalArgumentException (queue "baz" :bad :option))))

(deftest topic-should-validate-opts
  (is (thrown? IllegalArgumentException (topic "baz" :bad :option))))

(deftest destination-should-be-stoppable
  (stop (queue "foo")))

(deftest context-should-throw-with-invalid-mode
  (is (thrown? IllegalArgumentException
        (context :mode :blarg))))

(deftest context-should-work
  (with-open [c (context)]
    (is c)
    (is (instance? Context c))))

(deftest context-should-accept-kwargs-and-map
  (with-open [c (context :port 1234)]
    (is c))
  (with-open [c (context {:port 1234})]
    (is c)))

(deftest context-should-validate-opts
  (is (thrown? IllegalArgumentException (context :bad :option))))

(deftest publish-receive-should-work
  (let [q (queue "foo" :durable? false)]
    (publish q "hi")
    (is (= "hi" (receive q)))))

(deftest publish-should-accept-kwargs-and-map
  (publish (queue "foo") "hi" :encoding :none)
  (publish (queue "foo") "hi" {:encoding :none}))

(deftest publish-should-validate-opts
  (is (thrown? IllegalArgumentException (publish (queue "foo") 1 :bad :option))))

(deftest publish-should-preserve-metadata-as-properties
  (let [q (random-queue)]
    (publish q (with-meta {:x :y} {:ham "biscuit"}))
    (let [r (receive q :decode? false)]
      (is (= "biscuit" (get (.properties r) "ham"))))))

(deftest receive-should-restore-properties-to-metadata
  (let [q (random-queue)]
    (publish q (with-meta {:x :y} {:ham "biscuit"}))
    (let [r (receive q)]
      (is (= {"ham" "biscuit"} (meta r))))))

(deftest properties-option-should-override-metadata
  (let [q (random-queue)]
    (publish q (with-meta {:x :y} {:ham "biscuit"}) :properties {:eggs "gravy"})
    (let [r (receive q)]
      (is (= {"eggs" "gravy"} (meta r))))))

(deftest listen-should-work
  (let [p (promise)
        q (random-queue)]
    (with-open [listener (listen q #(deliver p %))]
      (publish q :hi)
      (is (= :hi (deref p 2000 :fail))))))

(deftest listen-should-restore-properties-to-metadata
  (let [p (promise)
        q (random-queue)]
    (with-open [listener (listen q #(deliver p (meta %)))]
      (publish q (with-meta {:x :y} {:ham "biscuit"}))
      (is (= {"ham" "biscuit"} (deref p 2000 :fail))))))

(deftest durable-subscriber
  (let [called (atom (promise))
        t (topic "subscribe")
        listener (subscribe t "my-sub" #(deliver @called %))]
    (publish t :hi)
    (is (= :hi (deref @called 1000 :fail)))
    (reset! called (promise))
    (stop listener)
    (publish t :hi-again)
    (is (= :fail (deref @called 100 :fail)))
    (with-open [listener (subscribe t "my-sub" #(deliver @called %))]
      (is (= :hi-again (deref @called 1000 :fail))))
    (unsubscribe t "my-sub")
    (reset! called (promise))
    (stop listener)
    (publish t :failure)
    (with-open [listener (subscribe t "my-sub" #(deliver @called %))]
      (is (= :success (deref @called 1000 :success))))))

(deftest durable-subscriber-with-context
  (with-open [context (context :client-id "my-sub")]
    (let [called (atom (promise))
          t (topic "subscribe")
          listener (subscribe t "my-sub" #(deliver @called %) :context context)]
      (publish t :hi)
      (is (= :hi (deref @called 1000 :fail)))
      (reset! called (promise))
      (stop listener)
      (publish t :hi-again)
      (is (= :fail (deref @called 100 :fail)))
      (with-open [listener (subscribe t "my-sub" #(deliver @called %) :context context)]
        (is (= :hi-again (deref @called 1000 :fail))))
      (unsubscribe t "my-sub" :context context)
      (reset! called (promise))
      (stop listener)
      (publish t :failure)
      (with-open [listener (subscribe t "my-sub" #(deliver @called %) :context context)]
        (is (= :success (deref @called 1000 :success)))))))

(def ^:dynamic *dvar* :unbound)

(deftest listen-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (let [p (promise)
          q (queue "listen-queue")]
      (with-open [listener (listen q (fn [_] (deliver p *dvar*)))]
        (publish q :whatevs)
        (is (= :bound (deref p 2000 :fail)))))))

(deftest request-respond-should-work
  (let [q (queue "req-resp")]
    (with-open [listener (respond q keyword)]
      (is (= :hi (deref (request q "hi") 10000 :fail))))))

(deftest request-respond-should-work-with-a-local-context
  (let [q (random-queue)]
    (with-open [listener (respond q keyword)
                c (context)]
      (is (= :hi (deref (request q "hi" :context c) 10000 :fail))))))

(deftest request-should-restore-properties-to-metadata
  (let [q (random-queue)]
    (with-open [listener (respond q identity)]
      (let [result (deref (request q (with-meta {:x :y} {:ham "biscuit"})) 2000 :fail)]
        (is (= {"ham" "biscuit"} (meta result)))))))

(deftest respond-future-should-handle-a-timeout
  (let [q (queue "req-resp")]
    (with-open [listener (respond q (fn [m] (Thread/sleep 100) (keyword m)))]
      (let [response (request q "hi")]
        (is (= :timeout (deref response 1 :timeout)))
        (is (= :hi (deref response 1000 :timeout)))))))

(deftest respond-should-work-with-conveyed-bindings
  (binding [*dvar* :bound]
    (let [q (queue "rr-queue" :durable? false)]
      (with-open [listener (respond q (fn [_] *dvar*))]
        (is (= :bound (deref (request q :whatevs) 2000 :fail)))))))

(deftest request-respond-should-coordinate-results
  (let [q (queue "rr-queue" :durable? false)]
    (with-open [listener (respond q (fn [m] (Thread/sleep m) m) :concurrency 5)]
      (let [r1 (request q 50)
            r2 (request q 100)
            r3 (request q 25)]
        (is (= 50 (deref  r1 2000 :fail)))
        (is (= 100 (deref r2 2000 :fail)))
        (is (= 25 (deref r3 2000 :fail)))))))

(deftest remote-request-respond-should-work
  (queue "remote-req-resp" :durable? false)
  (let [extra-connect-opts
        (cond-> []
          (u/in-container?) (conj :username test-user :password test-password)
          (and (u/in-container?) (not (u/in-eap?))) (conj :remote-type :hornetq-wildfly))]
    (with-open [c (apply context :host "localhost" :port (u/messaging-remoting-port)
                    extra-connect-opts)]
      (let [q (queue "remote-req-resp" :context c)]
        (with-open [listener (respond q keyword)]
          (is (= :hi (deref (request q "hi") 60000 :fail))))))))

(deftest remote-listen-should-work
  (queue "remote-listen" :durable? false)
  (let [extra-connect-opts
        (cond-> []
          (u/in-container?) (conj :username test-user :password test-password)
          (and (u/in-container?) (not (u/in-eap?))) (conj :remote-type :hornetq-wildfly))]
    (with-open [c (apply context :host "localhost" :port (u/messaging-remoting-port)
                    extra-connect-opts)]
      (let [q (queue "remote-listen" :context c)
            p (promise)]
        (with-open [listener (listen q (partial deliver p))]
          (publish q :hi)
          (is (= :hi (deref p 60000 :fail))))))))

(deftest transactional-context-should-work
  (let [q (queue "tx-queue" :durable? false)]
    (with-open [s (context :mode :transacted)]
      (publish q :first :context s)
      (.commit s)
      (publish q :second :context s)
      (.rollback s))
    (is (= :first (receive q :timeout 100 :timeout-val :failure)))
    (is (= :success (receive q :timeout 100 :timeout-val :success)))))

(deftest remote-context-should-work
  (queue "remote" :durable? false)
  (let [extra-connect-opts
        (cond-> []
          (u/in-container?) (conj :username test-user :password test-password)
          (and (u/in-container?) (not (u/in-eap?))) (conj :remote-type :hornetq-wildfly))]
    (with-open [c (apply context :host "localhost" :port (u/messaging-remoting-port)
                    extra-connect-opts)]
      (let [q (queue "remote" :context c)]
        (publish q :hi)
        (= :hi (receive q :timeout 100 :timeout-val :failure))))))

;; This works sometimes, but sometimes a remote receive takes 30s to
;; timeout, no matter the :timeout setting. I think we're doing it
;; right, and it's an HQ issue, so I'm disabling this test.

#_(deftest remote-receive-should-properly-timeout
  (let [q-name (.name (random-queue))
        extra-connect-opts
        (cond-> []
          (u/in-container?) (conj :username test-user :password test-password)
          (and (u/in-container?) (not (u/in-eap?))) (conj :remote-type :hornetq-wildfly))]
    (with-open [c (apply context :host "localhost" :port (u/messaging-remoting-port)
                    extra-connect-opts)]
      (let [q (queue q-name :context c)
            start (System/currentTimeMillis)]
        ;; warm up the context
        (receive q :timeout 1)
        (is (= :success (receive q :timeout 100 :timeout-val :success)))
        (is (< (- (System/currentTimeMillis) start) 200))))))

(deftest request-receive-should-work-with-recreated-queue
  (let [q (random-queue)
        action (fn [q msg]
                 (respond q identity)
                 (is (= msg (deref (request q msg) 1000 :failure)))
                 (stop q))]
    (action q :hi)
    (action (queue (.name q)) :hi-again)))

(deftest publish-from-a-listener-should-work
  (let [q (random-queue)
        q2 (random-queue)
        l (listen q #(publish q2 %))]
    (try
      (publish q :ham)
      (is (= :ham (receive q2 :timeout 1000 :timeout-val :failure)))
      (finally
        (.close l)))))

(deftest publish-from-a-listener-that-throws-should-fail
  (let [q (random-queue)
        q2 (random-queue)
        l (listen q (fn [m]
                      (publish q2 m)
                      (throw (Exception. "expected exception"))))]
    (try
      (publish q :failure)
      (is (= :success (receive q2 :timeout 1000 :timeout-val :success)))
      (finally
        (.close l)))))

(deftest publish-from-a-non-transacted-listener-that-throws-should-succeed
  (let [q (random-queue)
        q2 (random-queue)
        l (listen q (fn [m]
                      (publish q2 m)
                      (throw (Exception. "expected exception")))
            :mode :auto-ack)]
    (try
      (publish q :success)
      (is (= :success (receive q2 :timeout 1000 :timeout-val :failure)))
      (finally
        (.close l)))))

(deftest publish-to-a-remote-queue-from-a-listener-should-work
  (queue "remote" :durable? false)
  (let [extra-connect-opts
        (cond-> []
          (u/in-container?) (conj :username test-user :password test-password)
          (and (u/in-container?) (not (u/in-eap?))) (conj :remote-type :hornetq-wildfly))
        conn (apply context :host "localhost" :port (u/messaging-remoting-port)
               extra-connect-opts)
        q (random-queue)
        remote-q (queue "remote" :context conn)
        l (listen q #(publish remote-q %))]
    (try
      (publish q :ham)
      (is (= :ham (receive (queue "remote") :timeout 1000 :timeout-val :failure)))
      (finally
        (.close conn)
        (.close l)))))

(defrecord TestRecord [x])

(deftest records-should-be-publishable-via-the-:none-encoding
  (let [q (random-queue)
        p (promise)]
    (with-open [l (listen q #(deliver p %))]
      (publish q (->TestRecord 1) :encoding :none)
      (is (= (->TestRecord 1) (deref p 10000 :timeout))))))
