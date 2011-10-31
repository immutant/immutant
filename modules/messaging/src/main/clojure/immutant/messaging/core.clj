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

(ns immutant.messaging.core
  (:use [immutant.utilities :only (at-exit)])
  (:import (javax.jms Session))
  (:require [immutant.registry :as lookup])
  (:require [immutant.messaging.hornetq-direct :as hornetq]))

(defn queue? [name]
  (.startsWith name "/queue"))

(defn topic? [name]
  (.startsWith name "/topic"))

(def connection-factory
  (if-let [reference-factory (lookup/service "jboss.naming.context.java.ConnectionFactory")]
    (let [reference (.getReference reference-factory)]
      (try
        (.getInstance reference)
        (finally (at-exit #(do (.release reference) (println "JC: released" reference))))))
    (do
      (println "WARN: unable to obtain JMS Connection Factory so we must be outside container")
      hornetq/connection-factory)))

(defn create-session [connection]
  (.createSession connection false Session/AUTO_ACKNOWLEDGE))

(defn with-session [f]
  (with-open [connection (.createConnection connection-factory)
              session (create-session connection)]
    (.start connection)
    (f session)))

(defn destination [session name]
  (cond
   (queue? name) (.createQueue session name)
   (topic? name) (.createTopic session name)
   :else (throw (Exception. "Illegal destination name"))))

(defn stop-queue [name]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (.destroyQueue manager name)))
  
(defn start-queue [name & {:keys [durable selector] :or {durable false selector ""}}]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (and (.createQueue manager false name selector durable (into-array String []))
         (at-exit #(do (stop-queue name) (println "JC: stopped queue" name))))
    (throw (Exception. (str "Unable to start queue, " name)))))

(defn stop-topic [name]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (.destroyTopic manager name)))
  
(defn start-topic [name & opts]
  (if-let [manager (lookup/service "jboss.messaging.default.jms.manager")]
    (and (.createTopic manager false name (into-array String []))
         (at-exit #(do (stop-topic name) (println "JC: stopped topic" name))))
    (throw (Exception. (str "Unable to start topic, " name)))))

