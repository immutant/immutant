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

(ns ^:no-doc ^:internal immutant.messaging.internal
    (:import (javax.jms Queue Topic)))

(defrecord QueueMarker [name]
  Object
  (toString [_] name))

(defrecord TopicMarker [name]
  Object
  (toString [_] name))

(defn queue-name? [name]
  (or (instance? QueueMarker name)
      (and (instance? String name) 
           (.contains name "queue"))))

(defn queue? [queue]
  (or (instance? Queue queue)
      (queue-name? queue)))

(defn topic-name? [name]
  (or (instance? TopicMarker name)
      (and (instance? String name) 
           (.contains name "topic"))))

(defn topic? [topic]
  (or (instance? Topic topic)
      (topic-name? topic)))

