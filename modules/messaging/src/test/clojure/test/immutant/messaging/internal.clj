;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

(ns test.immutant.messaging.internal
  (:use immutant.messaging.internal
        clojure.test)
  (:import [immutant.messaging.internal QueueMarker TopicMarker]))

(deftest queue-names
  (are [name q?] (= q? (queue-name? name))
       "/queue"             true
       "/queuebar"          true
       "/queue/foo"         true
       "/queue.ham"         true
       ".queue"             true
       ".queuebar"          true
       ".queue/foo"         true
       ".queue.ham"         true
       "..queue"            true
       "..queuebar"         true
       "..queue/foo"        true
       "..queue.ham"        true
       "queue"              true
       "queuebar"           true
       "queue/foo"          true
       "queue.ham"          true
       (QueueMarker. "foo") true
       "que"                false))

(deftest topic-names
  (are [name q?] (= q? (topic-name? name))
       "/topic"             true
       "/topicbar"          true
       "/topic/foo"         true
       "/topic.ham"         true
       ".topic"             true
       ".topicbar"          true
       ".topic/foo"         true
       ".topic.ham"         true
       "..topic"            true
       "..topicbar"         true
       "..topic/foo"        true
       "..topic.ham"        true
       "topic"              true
       "topicbar"           true
       "topic/foo"          true
       "topic.ham"          true
       (TopicMarker. "foo") true
       "top"                false))
