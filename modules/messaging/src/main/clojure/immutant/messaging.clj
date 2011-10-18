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

(ns immutant.messaging
  (:import (org.hornetq.jms.client HornetQDestination))
  (:import (org.hornetq.core.remoting.impl.netty TransportConstants))
  (:import (org.hornetq.api.core TransportConfiguration))
  (:import (org.hornetq.api.jms HornetQJMSClient JMSFactoryType))
  (:import (javax.jms Session)))

(declare produce consume encode decode)

(defn publish [destination message]
  "Send a message to a destination"
  (produce destination (encode message)))
    
(defn receive [destination & opts]
  "Receive a message from a destination"
  (decode (consume destination opts)))

(defn wait-for-destination [f & count]
  (let [attempts (or count 30)
        retry #(do (Thread/sleep 1000) (wait-for-destination f (dec attempts)))]
    (try
      (f)
      (catch javax.naming.NameNotFoundException e
        (if (> attempts 0) (retry) (throw e)))
      (catch javax.jms.JMSException e
        (if (> attempts 0) (retry) (throw e))))))

;; privates

(def connection-factory 
  (let [connect_opts { TransportConstants/PORT_PROP_NAME (Integer. 5445) }
        transport_config (new TransportConfiguration "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory" connect_opts)]
    (HornetQJMSClient/createConnectionFactoryWithoutHA JMSFactoryType/CF (into-array [transport_config]))))

(defn- with-connection [f]
  (let [connection (.createConnection connection-factory)]
    (try
      (.start connection)
      (f connection)
      (finally (.close connection)))))
  
(defn- with-session [f]
  (with-connection (fn [connection]
                     (let [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
                       (try
                         (f session)
                         (finally (.close session)))))))

(defn- java-destination [destination]
  (if (.contains destination "queue")
    (HornetQDestination/fromAddress (str "jms.queue." destination ))
    (HornetQDestination/fromAddress (str "jms.topic." destination ))))

(defn- produce [destination message]
  (with-session (fn [session]
                  (let [producer (.createProducer session (java-destination destination))
                        jms-msg (.createTextMessage session message)]
                    (.send producer jms-msg)))))

(defn- consume [destination {timeout :timeout}]
  (with-session (fn [session]
                  (let [consumer (.createConsumer session (java-destination destination))
                        message (.receive consumer (or timeout 10000))]
                    (and message (.getText message))))))

(defn- encode [message]
  "Stringify a clojure data structure"
  (with-out-str (binding [*print-dup* true] (pr message))))

(defn- decode [message]
  "Turn a string into a clojure data structure"
  (and message (load-string message)))
