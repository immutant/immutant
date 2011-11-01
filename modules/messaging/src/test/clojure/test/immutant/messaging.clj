(ns test.immutant.messaging
  (:require [immutant.registry :as lookup])
  (:use [immutant.messaging])
  (:use [clojure.test]))

(deftest start-queue-outside-of-container
  (is (thrown-with-msg? Exception #"Unable to start queue" (start "/queue/barf"))))

(deftest start-topic-outside-of-container
  (is (thrown-with-msg? Exception #"Unable to start topic" (start "/topic/barf"))))

(deftest queue-already-running
  (with-redefs [lookup/fetch (fn [name]
                                 (is (= name "jboss.messaging.default.jms.manager"))
                                 (proxy [org.hornetq.jms.server.JMSServerManager][]
                                   (createQueue [& _] false)))]
    (is (= false (start "/queue/foo")))))

(deftest topic-already-running
  (with-redefs [lookup/fetch (fn [name]
                                 (is (= name "jboss.messaging.default.jms.manager"))
                                 (proxy [org.hornetq.jms.server.JMSServerManager][]
                                   (createTopic [& _] false)))]
    (is (= false (start "/topic/foo")))))

(deftest bad-destination-name
  (is (thrown-with-msg? Exception #"names must start with" (start "/bad/name"))))

