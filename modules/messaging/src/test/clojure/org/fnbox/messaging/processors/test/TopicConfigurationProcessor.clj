;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

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

