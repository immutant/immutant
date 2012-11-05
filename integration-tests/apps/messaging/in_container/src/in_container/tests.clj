(ns in-container.tests
  (:use clojure.test)
  (:require [immutant.messaging :as msg]
            [immutant.registry  :as reg]))

(deftest listen-on-a-queue-should-raise-for-non-existent-destinations
  (is (thrown? IllegalStateException
               (msg/listen "a.non-existent.queue" (constantly nil)))))

(deftest listen-on-a-topic-should-raise-for-non-existent-destinations
  (is (thrown? IllegalStateException
               (msg/listen "a.non-existent.topic" (constantly nil)))))

(deftest queue-start-should-be-synchronous
  (let [queue "queue.start.sync"]
    (msg/start queue)
    ;; this should throw if the queue doesn't yet exist
    (is (msg/listen queue (constantly true)))))

(deftest topic-start-should-be-synchronous
  (let [topic "topic.start.sync"]
    (msg/start topic)
    ;; this should throw if the topic doesn't exist
    (is (msg/listen topic (constantly true)))))

(deftest queue-stop-should-be-synchronous
  (let [queue "queue.stop.sync"]
    (msg/start queue)
    (msg/stop queue)
    (is (thrown? IllegalStateException
                 (msg/listen queue (constantly true))))))

(deftest topic-stop-should-be-synchronous
  (let [topic "topic.stop.sync"]
    (msg/start topic)
    (msg/stop topic)
    (is (thrown? IllegalStateException
                 (msg/listen topic (constantly true))))))

(deftest queue-start-should-be-idempotent
  (let [queue "queue.id"]
    (msg/start queue)
    (try
      (msg/start queue)
      (is true)
      (catch Exception e
          (.printStackTrace e)
          (is false)))))


(deftest topic-start-should-be-idempotent
  (let [topic "topic.id"]
    (msg/start topic)
    (try
      (msg/start topic)
      (is true)
      (catch Exception e
          (.printStackTrace e)
          (is false)))))

(deftest unlisten-on-a-queue-should-be-synchronous
  (let [queue "queue.ham"]
    (msg/start queue)
    (msg/unlisten (msg/listen queue (constantly nil)))
    (is (= true (msg/stop queue)))))

(deftest unlisten-on-a-topic-should-be-synchronous
  (let [topic "topic.ham"]
    (msg/start topic)
    (msg/unlisten (msg/listen topic (constantly nil)))
    (is (= true (msg/stop topic)))))

(deftest force-stop-on-a-queue-should-remove-listeners
  (let [queue "queue.force"
        izer (reg/fetch "message-processor-groupizer")]
    (msg/start queue)
    (msg/listen queue (constantly nil))
    (msg/stop queue :force true)
    (is (not (seq (.installedGroupsFor izer queue))))))

(deftest force-stop-on-a-topic-should-remove-listeners
  (let [topic "topic.force"
        izer (reg/fetch "message-processor-groupizer")]
    (msg/start topic)
    (msg/listen topic (constantly nil))
    (msg/stop topic :force true)
    (is (not (seq (.installedGroupsFor izer topic))))))
