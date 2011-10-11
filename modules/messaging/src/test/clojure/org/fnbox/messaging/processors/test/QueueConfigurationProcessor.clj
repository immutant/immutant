(ns org.fnbox.messaging.processors.test.QueueConfigurationProcessor
  (:use clojure.test)
  (:use fnbox.test.helpers)
  (:use fnbox.test.as.helpers)
  (:import [org.fnbox.core.processors AppCljParsingProcessor])
  (:import [org.fnbox.core ClojureMetaData])
  (:import [org.fnbox.messaging.processors QueueConfigurationProcessor])
  (:import [org.projectodd.polyglot.messaging.destinations QueueMetaData])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(AppCljParsingProcessor.)
                             (QueueConfigurationProcessor.)]))

(deftest it-should-not-attach-metadata-if-no-queue-specified
  (let [unit (.deployResourceAs *harness* (io/resource "destinationless-descriptor.clj") "app.clj" )]
    (is (= 0 (.size (.getAttachmentList unit QueueMetaData/ATTACHMENTS_KEY))))))

(deftest it-should-attach-metadata-for-each-queue-specified
  (let [unit (.deployResourceAs *harness* (io/resource "queue-descriptor.clj") "app.clj" )]
    (is (= 3 (.size (.getAttachmentList unit QueueMetaData/ATTACHMENTS_KEY))))))

(defn find-metadata [list name]
  (first (filter #(= (.getName %) name) list)))

(deftest it-should-set-the-name-on-the-metadata
  (let [unit (.deployResourceAs *harness* (io/resource "queue-descriptor.clj") "app.clj" )
        metadatas (.getAttachmentList unit QueueMetaData/ATTACHMENTS_KEY)]
    (are-not [q] (nil? (find-metadata metadatas q))
             "/queue/one"
             "/queue/two"
             "/queue/three")))

(deftest it-should-set-durable-if-requested
  "Pending normalization of descriptor option keys"
  (let [unit (.deployResourceAs *harness* (io/resource "queue-descriptor.clj") "app.clj" )
        metadatas (.getAttachmentList unit QueueMetaData/ATTACHMENTS_KEY)]
    (are [q durable] (= durable (.isDurable (find-metadata metadatas q)))
             "/queue/one" true
             "/queue/two" false)))

(deftest it-should-be-durable-if-not-specified
  (let [unit (.deployResourceAs *harness* (io/resource "queue-descriptor.clj") "app.clj" )
        metadatas (.getAttachmentList unit QueueMetaData/ATTACHMENTS_KEY)]
    (is (= true (.isDurable (find-metadata metadatas "/queue/three"))))))
