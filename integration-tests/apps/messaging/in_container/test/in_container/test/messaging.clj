(ns in-container.test.messaging
  (:use clojure.test)
  (:require [immutant.messaging :as msg]
            [immutant.registry  :as registry]))

(defn get-destination [name]
  (let [destinations (.getDestinations (registry/get "destinationizer"))]
    (.get destinations (str name))))

(testing "listen"
  (deftest listen-on-a-queue-should-raise-for-non-existent-destinations
    (is (thrown? IllegalStateException
                 (msg/listen "a.non-existent.queue" (constantly nil)))))

  (deftest listen-on-a-topic-should-raise-for-non-existent-destinations
    (is (thrown? IllegalStateException
                 (msg/listen "a.non-existent.topic" (constantly nil)))))

  (deftest remote-listen-on-a-queue-should-work
    (let [queue "remote.queue"
          response-q "remote.queue.response"]
      (msg/start queue) ;; it's in-container, but we'll pretend it isn't below
      (msg/start response-q)
      (msg/listen queue #(msg/publish response-q %) :host "integ-app1.torquebox.org" :port 5445)
      (msg/publish queue "ahoy" :host "integ-app1.torquebox.org" :port 5445)
      (is (= "ahoy" (msg/receive response-q))))))

(testing "start for queues"
  (deftest queue-start-should-be-synchronous
    (let [queue "queue.start.sync"]
      (msg/start queue)
      ;; this should throw if the queue doesn't yet exist
      (is (msg/listen queue (constantly true)))))

  (deftest queue-start-should-work-with-as-queue
    (let [q (msg/as-queue "hambone")]
      (msg/start q)
      (is (get-destination q))))

  (deftest queue-start-should-be-idempotent
    (let [queue "queue.id"]
      (msg/start queue)
      (try
        (msg/start queue)
        (is true)
        (catch Exception e
          (.printStackTrace e)
          (is false))))))

(testing "start for topics"
  (deftest topic-start-should-be-synchronous
    (let [topic "topic.start.sync"]
      (msg/start topic)
      ;; this should throw if the topic doesn't exist
      (is (msg/listen topic (constantly true)))))

  (deftest topic-start-should-work-with-as-topic
    (let [q (msg/as-topic "pigbone")]
      (msg/start q)
      (is (get-destination q))))

  (deftest topic-start-should-be-idempotent
    (let [topic "topic.id"]
      (msg/start topic)
      (try
        (msg/start topic)
        (is true)
        (catch Exception e
          (.printStackTrace e)
          (is false))))))

(testing "stop for queues"
  (deftest queue-stop-should-be-synchronous
    (let [queue "queue.stop.sync"]
      (msg/start queue)
      (msg/stop queue)
      (is (thrown? IllegalStateException
                   (msg/listen queue (constantly true))))))

  (deftest queue-stop-should-work-with-as-queue
    (let [queue (msg/as-queue "dogleg")]
      (msg/start queue)
      (msg/stop queue)
      (is (not (get-destination queue)))))

  (deftest force-stop-on-a-queue-should-remove-listeners
    (let [queue "queue.force"
          izer (registry/get "message-processor-groupizer")]
      (msg/start queue)
      (msg/listen queue (constantly nil))
      (msg/stop queue :force true)
      (is (not (seq (.installedGroupsFor izer queue)))))))

(testing "stop for topics"
  (deftest topic-stop-should-be-synchronous
    (let [topic "topic.stop.sync"]
      (msg/start topic)
      (msg/stop topic)
      (is (thrown? IllegalStateException
                   (msg/listen topic (constantly true))))))

  (deftest topic-stop-should-work-with-as-topic
    (let [topic (msg/as-topic "dogsnout")]
      (msg/start topic)
      (msg/stop topic)
      (is (not (get-destination topic)))))

  (deftest force-stop-on-a-topic-should-remove-listeners
    (let [topic "topic.force"
          izer (registry/get "message-processor-groupizer")]
      (msg/start topic)
      (msg/listen topic (constantly nil))
      (msg/stop topic :force true)
      (is (not (seq (.installedGroupsFor izer topic)))))))

(testing "unlisten"
  (deftest unlisten-on-a-queue-should-be-synchronous
    (let [queue "queue.ham"]
      (msg/start queue)
      (msg/unlisten (msg/listen queue (constantly nil)))
      (is (= true (msg/stop queue)))))

  (deftest unlisten-on-a-topic-should-be-synchronous
    (let [topic "topic.ham"]
      (msg/start topic)
      (msg/unlisten (msg/listen topic (constantly nil)))
      (is (= true (msg/stop topic))))))

