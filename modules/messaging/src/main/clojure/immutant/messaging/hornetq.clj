;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns ^{:no-doc true} immutant.messaging.hornetq
    (:import org.hornetq.jms.client.HornetQDestination
             org.hornetq.api.core.TransportConfiguration
             (org.hornetq.api.jms HornetQJMSClient JMSFactoryType)))

(defn set-retry-options
  "Call retry setters on connection factory"
  [factory opts]
  (let [fmap {:retry-interval #(.setRetryInterval factory %)
              :retry-interval-multiplier #(.setRetryIntervalMultiplier factory %)
              :reconnect-attempts #(.setReconnectAttempts factory %)
              :max-retry-interval #(.setMaxRetryInterval factory %)}
        retry-opts (select-keys opts (keys fmap))]
    (doseq [[setter val] retry-opts :when (number? val)]
      ((setter fmap) val)))
  factory)

(defn connection-factory
 "Create a connection factory, typically invoked when outside container"
 ([]
    (connection-factory nil))
 ([{:keys [host port] :or {host "localhost" port 5445} :as opts}]
    (let [connect_opts { "host" host "port" (Integer. (int port)) }
          transport_config (TransportConfiguration.
                            "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"
                            connect_opts)
          factory (HornetQJMSClient/createConnectionFactoryWithoutHA
                   JMSFactoryType/CF
                   ^"[Lorg.hornetq.api.core.TransportConfiguration;"
                   (into-array [transport_config]))]
      (set-retry-options factory opts))))
