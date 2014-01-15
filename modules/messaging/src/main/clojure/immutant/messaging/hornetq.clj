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

(ns immutant.messaging.hornetq
  "HornetQ specific messaging functionality."
  (:use immutant.messaging.internal)
  (:require [immutant.registry :as registry]
            [immutant.util :as u])
  (:import org.hornetq.jms.client.HornetQDestination
           (org.hornetq.api.core SimpleString TransportConfiguration)
           (org.hornetq.api.jms HornetQJMSClient JMSFactoryType)))

(defn- hornetq-server
  "Retrieves the current HornetQ server instance from the registry"
  []
  (registry/get "jboss.messaging.default"))

(defn destination-controller
  "Returns the destination controller for the given name, or nil if
   the destination doesn't exist. name can either be a String or the
   result of calling messaging/as-queue or messaging/as-topic.

   The returned controller depends on the type of the given
   destination and, for queues, the requested control-type (which
   defaults to :jms):

   destination  control-type  controller
   -------------------------------------------------------------------------
   Queue        :jms          org.hornetq.api.jms.management.JMSQueueControl
   Queue        :core         org.hornetq.core.management.impl.QueueControl
   Topic        <ignored>     org.hornetq.api.jms.management.TopicControl

   Refer to the javadocs for those control classes for details on the
   available operations."
  ([name]
     (destination-controller name :jms))
  ([name control-type]
     (when-let [hq-server (hornetq-server)]
       (.getResource (.getManagementService hq-server)
         (if (and (queue-name? name) (= :core control-type)) 
           (core-queue-name name)
           (jms-dest-name name))))))

(defn ^:no-doc set-retry-options
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

(defn ^:no-doc connection-factory
 "Create a connection factory, typically invoked when outside container"
 ([]
    (connection-factory nil))
 ([{:keys [host port] :or {host "localhost" port 5445} :as opts}]
    (let [connect_opts { "host" host "port" (Integer. (int port))}
          transport_config (TransportConfiguration.
                            "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"
                            connect_opts)
          factory (HornetQJMSClient/createConnectionFactoryWithoutHA
                   JMSFactoryType/CF
                   ^"[Lorg.hornetq.api.core.TransportConfiguration;"
                   (into-array [transport_config]))]
      (set-retry-options factory opts))))

(def ^:private address-settings-coercions
  {:address-full-message-policy (fn [addr]
                                  (u/when-import 'org.hornetq.core.settings.impl.AddressFullMessagePolicy
                                    (eval `(case (name ~addr)
                                             "block" AddressFullMessagePolicy/BLOCK
                                             "drop"  AddressFullMessagePolicy/DROP
                                             "fail"  AddressFullMessagePolicy/FAIL
                                             "page"  AddressFullMessagePolicy/PAGE))))
   :dead-letter-address #(SimpleString. (jms-dest-name %))
   :expiry-address #(SimpleString. (jms-dest-name %))
   :last-value-queue boolean
   :send-to-dla-on-no-route boolean})

(def ^:private address-settings-aliases
  {:send-to-dla-on-no-route :send-to-d-l-a-on-no-route})

(defn ^:private normalize-destination-match [match]
  (cond
    (= "#" match)             match
    (destination-name? match) (jms-dest-name match)
    :else                     (throw (destination-name-error match))))

(defn set-address-options
  "Sets the HornetQ-specific address options for the given match.
   This provides programatic access to options that are normally set
   in the xml configuration.  match must be contain either 'queue' or
   'topic', be the result of calling as-queue or as-topic, or a fully
   qualified jms destination name (prefixed with jms.queue. or
   jms.topic.). It may contain HornetQ wildcard matchers (see
   http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/wildcard-syntax.html).

   The following settings are supported [default value]:
   
   * :address-full-message-policy [:page] - Specifies what should
     happen when an address reaches :max-size-bytes in undelivered
     messages. Options are:
      * :block - publish calls will block until the current size
        drops below :max-size-bytes
      * :drop - new messages are silently dropped
      * :fail - new messages are dropped and an exception is thrown on publish
      * :page - new messages will be paged to disk
     See http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :dead-letter-address [jms.queue.DLQ] - If set, any messages that
     fail to deliver to their original destination will be delivered
     here. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/undelivered-messages.html#undelivered-messages.configuring

   * :expiry-address [jms.queue.ExpiryQueue] - If set, any messages
     with a :ttl that expires before delivery will be delivered
     here. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/message-expiry.html#message-expiry.configuring

   * :expiry-delay [-1] - If > -1, this value (in ms) is used as the
     default :ttl for messages that don't have a :ttl > 0 set. 

   * :last-value-queue [false] - If true, only the most recent message
      for a last-value property will be retained. See
      http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/last-value-queues.html

   * :max-delivery-attempts [10] - The number of times delivery will
     be attempted for a message before giving up. If :dead-letter-address
     is set, the message will be delivered there, or removed otherwise. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/undelivered-messages.html#undelivered-messages.configuring

   * :max-size-bytes [20971520 (20MB)] - The maximum size (in bytes) of retained messages
     on an address before :address-full-message-policy is applied. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :page-cache-max-size [5] - HornetQ will keep up to this many page files in
     memory to optimize IO. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :page-size-bytes [10485760 (10MB)] - The size (in bytes) of the page files created
     when paging. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :redelivery-delay [0] - Specifies the delay (in ms) between
     redelivery attempts. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/undelivered-messages.html#undelivered-messages.delay

   * :redelivery-multiplier [1.0] - Controls the backoff for redeliveries. The
     delay between redelivery attempts is calculated as
     :redelivery-delay * (:redelivery-multiplier ^ attempt-count)

   * :redistribution-delay [1000] - Specifies the delay (in ms) to wait before
     redistributing messages from a node in a cluster to other nodes when the
     queue no longer has consumers on the current node. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/clusters.html

   * :send-to-dla-on-no-route [false] - If true, any message that can't be
     routed to its destination will be sent to :dead-letter-address. 

   Calling this function again with the same match will override
   replace any previous settings for that match."
  [match settings]
  (u/when-import 'org.hornetq.core.settings.impl.AddressSettings
    (when (seq settings)
      (-> (hornetq-server)
        .getAddressSettingsRepository
        (.addMatch (normalize-destination-match match)
          (reduce
            (fn [s [k v]]
              (doto s
                (u/set-bean-property
                  (k address-settings-aliases k)
                  ((address-settings-coercions k identity) v))))
            (eval '(AddressSettings.))
            settings))))))

