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
