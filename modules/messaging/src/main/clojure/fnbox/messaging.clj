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

(ns fnbox.messaging
  (:import (org.hornetq.jms.client HornetQDestination))
  (:import (org.hornetq.core.remoting.impl.netty TransportConstants))
  (:import (org.hornetq.api.core TransportConfiguration))
  (:import (org.hornetq.api.jms HornetQJMSClient JMSFactoryType))
  (:import (javax.jms Session)))

(declare with-producer-for with-consumer-for encode decode)

(defn publish [destination message]
  "Send a message to a destination"
  (with-producer-for destination #(.send % (encode message))))
    
(defn receive [destination]
  "Receive a message from a destination"
  (with-consumer-for destination #(decode (.receive %))))

;; privates

(def connection-factory 
  (let [connect_opts { TransportConstants/PORT_PROP_NAME (int 5445) }
        transport_config (new TransportConfiguration "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory" connect_opts)]
    (HornetQJMSClient/createConnectionFactoryWithoutHA JMSFactoryType/CF (into-array [transport_config]))))

(defn- with-connection [f]
  (println "JC: connection-factory=" connection-factory)
  (let [connection (.createConnection connection-factory)]
    (try
      (println "JC: connection=" connection)
      (f connection)
      (finally (.close connection)))))
  
(defn- with-session [f]
  (with-connection (fn [connection]
                     (let [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
                       (try
                         (println "JC: session=" session)
                         (f session)
                         (finally (.close session)))))))

(defn- java-destination [destination]
  (if (.contains destination "queue")
    (HornetQDestination/fromAddress (str "jms.queue." destination ))
    (HornetQDestination/fromAddress (str "jms.topic." destination ))))

(defn- with-producer-for [destination f]
  (with-session (fn [session]
                  (let [producer (.createProducer session (java-destination destination))]
                    (try
                      (println "JC: producer=" producer)
                      (f producer)
                      (finally (.close producer)))))))

(defn- with-consumer-for [destination f]
  (with-session (fn [session]
                  (let [consumer (.createConsumer session (java-destination destination))]
                    (try
                      (println "JC: consumer=" consumer)
                      (f consumer)
                      (finally (.close consumer)))))))

(defn- encode [message]
  "Stringify a clojure data structure"
  (with-out-str (binding [*print-dup* true] (pr message))))

(defn- decode [message]
  "Turn a string into a clojure data structure"
  (load-string message))
