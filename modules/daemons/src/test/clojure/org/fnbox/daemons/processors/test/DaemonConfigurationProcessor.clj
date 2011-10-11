(ns org.fnbox.daemons.processors.test.DaemonConfigurationProcessor
  (:use clojure.test)
  (:use fnbox.test.helpers)
  (:use fnbox.test.as.helpers)
  (:import [org.fnbox.core.processors AppCljParsingProcessor])
  (:import [org.fnbox.core ClojureMetaData])
  (:import [org.fnbox.daemons.processors DaemonConfigurationProcessor])
  (:import [org.fnbox.daemons DaemonMetaData])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(AppCljParsingProcessor.)
                             (DaemonConfigurationProcessor.)]))

(deftest it-should-not-attach-metadata-if-no-daemon-specified
  (let [unit (.deployResourceAs *harness* (io/resource "daemonless-descriptor.clj") "app.clj" )]
    (is (= 0 (.size (.getAttachmentList unit DaemonMetaData/ATTACHMENTS_KEY))))))

(deftest it-should-attach-metadata-for-each-daemon-specified
  (let [unit (.deployResourceAs *harness* (io/resource "daemon-descriptor.clj") "app.clj" )]
    (is (= 2 (.size (.getAttachmentList unit DaemonMetaData/ATTACHMENTS_KEY))))))

(defn find-metadata [list name]
  (first (filter #(= (.getName %) name) list)))

(deftest it-should-set-the-name-on-the-metadata
  (let [unit (.deployResourceAs *harness* (io/resource "daemon-descriptor.clj") "app.clj" )
        metadatas (.getAttachmentList unit DaemonMetaData/ATTACHMENTS_KEY)]
    (are-not [q] (nil? (find-metadata metadatas q))
             "daemon-one"
             "daemon-two")))

(deftest it-should-raise-with-no-start-function-specified
  (is (thrown? RuntimeException
               (.deployResourceAs *harness* (io/resource "functionless-descriptor.clj") "app.clj" ))))

(deftest-pending we-should-test-for-options-and-params-once-implemented)
