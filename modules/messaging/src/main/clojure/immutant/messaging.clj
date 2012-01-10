;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.messaging
  (:use [immutant.utilities :only (at-exit)])
  (:use [immutant.messaging.core])
  (:require [immutant.messaging.codecs :as codecs]))

(defn start 
  "Create a message destination; name should be prefixed with either /queue or /topic"
  [name & opts]
  (when-not (or (queue? name) (topic? name))
    (throw (Exception. "Destination names must start with /queue or /topic")))
  (if (queue? name)
    (apply start-queue name opts)
    (apply start-topic name opts)))

(defn stop 
  "Destroy a message destination. Typically not necessary since it
  will be done for you when your app is undeployed. This will fail
  with a warning if any handlers are listening"
  [name]
  (if (or (queue? name) (topic? name))
    (stop-destination name)
    (throw (Exception. "Illegal destination name"))))

(defn publish 
  "Send a message to a destination"
  [dest-name message & opts]
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        producer (.createProducer session destination)
                        encoded (codecs/encode session message opts)
                        {:keys [delivery priority ttl]} (wash-publish-options opts producer)]
                  (.send producer encoded delivery priority ttl)))))
    
(defn receive 
  "Receive a message from a destination"
  [dest-name & {timeout :timeout}]
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        consumer (.createConsumer session destination)
                        encoded (.receive consumer (or timeout 10000))]
                    (and encoded (codecs/decode encoded))))))

(defn message-seq
  "A lazy sequence of messages received from a destination"
  [dest-name]
  (lazy-seq (cons (receive dest-name :timeout 0) (message-seq dest-name))))

(defn listen 
  "The handler function, f, will receive any messages sent to dest-name."
  [dest-name f]
  (let [connection (.createConnection connection-factory)]
    (try
      (let [session (create-session connection)
            destination (destination session dest-name)
            consumer (.createConsumer session destination)]
        (.setMessageListener consumer (proxy [javax.jms.MessageListener] []
                                        (onMessage [message]
                                          (f (codecs/decode message)))))
        (at-exit #(.close connection))
        (.start connection))
      connection
      (catch Throwable e
        (.close connection)
        (throw e)))))

(defn unlisten
  "Pass the result of a call to listen to de-register the handler. You
  only need to do this if you wish to stop the handler's destination
  before your app is undeployed"
  [listener]
  (.close listener))