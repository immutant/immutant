(ns org.fnbox.messaging.processors.test.TopicConfigurationProcessor
  (:use clojure.test)
  (:use fnbox.test.helpers)
  (:use fnbox.test.as.helpers)
  (:import [org.fnbox.core.processors AppCljParsingProcessor])
  (:import [org.fnbox.core ClojureMetaData])
  (:import [org.fnbox.messaging.processors TopicConfigurationProcessor])
  (:import [org.projectodd.polyglot.messaging.destinations TopicMetaData])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(AppCljParsingProcessor.)
                             (TopicConfigurationProcessor.)]))

(deftest it-should-not-attach-metadata-if-no-topic-specified
  (let [unit (.deployResourceAs *harness* (io/resource "destinationless-descriptor.clj") "app.clj" )]
    (is (= 0 (.size (.getAttachmentList unit TopicMetaData/ATTACHMENTS_KEY))))))

(deftest it-should-attach-metadata-for-each-topic-specified
  (let [unit (.deployResourceAs *harness* (io/resource "topic-descriptor.clj") "app.clj" )]
    (is (= 2 (.size (.getAttachmentList unit TopicMetaData/ATTACHMENTS_KEY))))))

(defn find-metadata [list name]
  (first (filter #(= (.getName %) name) list)))

(deftest it-should-set-the-name-on-the-metadata
  (let [unit (.deployResourceAs *harness* (io/resource "topic-descriptor.clj") "app.clj" )
        metadatas (.getAttachmentList unit TopicMetaData/ATTACHMENTS_KEY)]
    (are-not [q] (nil? (find-metadata metadatas q))
             "/topic/one"
             "/topic/two")))

