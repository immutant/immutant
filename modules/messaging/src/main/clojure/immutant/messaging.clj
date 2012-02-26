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
  "Easily publish and receive messages containing any type of nested
   data structure to dynamically-created topics and queues. Message
   distribution is automatically load-balanced when clustered."
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

(defn publish 
  "Send a message to a destination

   The following options are supported [default]:
    :encoding     :clojure :json or :text [:clojure]
    :priority     0-9 or :low :normal :high :critical [4]
    :ttl          time to live, in ms [0=forever]
    :persistent   whether undelivered messages survive restarts [true]
    :properties   a hash to which selectors may be applied"
  [dest-name message & {:as opts}]
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        producer (.createProducer session destination)
                        encoded (set-properties! (codecs/encode session message opts)
                                                 (:properties opts))
                        {:keys [delivery priority ttl]} (wash-publish-options opts producer)]
                    (.send producer encoded delivery priority ttl)))))
    
(defn receive 
  "Receive a message from a destination

   The following options are supported [default]:
    :timeout    time in ms, after which nil is returned [10000]
    :selector   A JMS (SQL 92) expression matching message properties"
  [dest-name & {:keys [timeout selector]}]
  (with-session (fn [session]
                  (let [destination (destination session dest-name)
                        consumer (.createConsumer session destination selector)
                        encoded (.receive consumer (or timeout 10000))]
                    (and encoded (codecs/decode encoded))))))

(defn message-seq
  "A lazy sequence of messages received from a destination. Accepts same options as receive"
  [dest-name & opts]
  (lazy-seq (cons (apply receive dest-name opts) (message-seq dest-name))))

(defn listen 
  "The handler function, f, will receive any messages sent to dest-name.

   The following options are supported [default]:
    :concurrency   the number of threads handling messages [1]"
  [dest-name f & {:keys [concurrency selector] :or {concurrency 1}}]
  (let [connection (.createConnection connection-factory)]
    (try
      (dotimes [_ concurrency]
        (let [session (create-session connection)
              destination (destination session dest-name)
              consumer (.createConsumer session destination selector)]
          (.setMessageListener consumer (proxy [javax.jms.MessageListener] []
                                          (onMessage [message]
                                            (f (codecs/decode message)))))))
      (at-exit #(.close connection))
      (.start connection)
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

(defn stop 
  "Destroy a message destination. Typically not necessary since it
  will be done for you when your app is undeployed. This will fail
  with a warning if any handlers are listening"
  [name]
  (if (or (queue? name) (topic? name))
    (stop-destination name)
    (throw (Exception. "Illegal destination name"))))

