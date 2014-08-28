;; Copyright 2014 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.messaging.hornetq
  "Utilities specific to [HornetQ](http://hornetq.jboss.org/)"
  (:require [immutant.internal.options   :as o]
            [immutant.internal.util      :as u]
            [immutant.util               :as pu]
            [immutant.messaging.internal :refer [broker]])
  (:import (org.projectodd.wunderboss.messaging Destination Queue)
           org.hornetq.api.core.SimpleString))

(defn server-manager
  "Retrieves the local JMS server mananger instance."
  []
  (when-let [broker (broker nil)]
    (.jmsServerManager broker)))

(defn ^:private jms-name [dest]
  (if-let [wrapped-dest (:destination dest)]
    (.jmsName wrapped-dest)
    dest))

(defn destination-controller
  "Returns the destination controller for `destination`.

   `destination` should be the result of calling
   {{immutant.messaging/queue}} or {{immutant.messaging/topic}}.

   The returned controller depends on the type of the given
   destination and, for queues, the requested control-type (which
   defaults to :jms) (`destination`, `control-type` - controller type):

   * queue, :jms - [JMSQueueControl](http://docs.jboss.org/hornetq/2.4.0.Final/docs/api/hornetq-jms-client/org/hornetq/api/jms/management/JMSQueueControl.html)
   * queue, :core - [QueueControl](http://docs.jboss.org/hornetq/2.4.0.Final/docs/api/hornetq-client/org/hornetq/api/core/management/QueueControl.html)
   * topic, `_` - [TopicControl](http://docs.jboss.org/hornetq/2.4.0.Final/docs/api/hornetq-jms-client/org/hornetq/api/jms/management/TopicControl.html)

   Refer to the javadocs for those control classes for details on the
   available operations."
  ([destination]
     (destination-controller destination :jms))
  ([destination control-type]
     (when-let [hq-server (server-manager)]
       (.getResource (-> hq-server .getHornetQServer .getManagementService)
         (str (when (and (instance? Queue destination) (= :core control-type))
                "core.queue.")
           (jms-name destination))))))

(def ^:private address-settings-coercions
  {:address-full-message-policy (fn [addr]
                                  (u/when-import 'org.hornetq.core.settings.impl.AddressFullMessagePolicy
                                    (eval `(case (name ~addr)
                                             "block" AddressFullMessagePolicy/BLOCK
                                             "drop"  AddressFullMessagePolicy/DROP
                                             "fail"  AddressFullMessagePolicy/FAIL
                                             "page"  AddressFullMessagePolicy/PAGE))))
   :dead-letter-address #(SimpleString. (jms-name %))
   :expiry-address #(SimpleString. (jms-name %))
   :last-value-queue boolean
   :send-to-dla-on-no-route boolean})

(def ^:private address-settings-aliases
  {:send-to-dla-on-no-route :send-to-d-l-a-on-no-route})

(defn ^:private normalize-destination-match [match]
  (jms-name
    (if (or
          (:destination match)
          (= "#" match)
          (re-find #"^jms\.(queue|topic)\." match))
      match
      (throw
        (IllegalArgumentException.
          (format "%s isn't a valid match. See the docs for set-address-options"))))))

(defn ^:private set-companion-options
  "Checks for options that need to be set together. Currently just
  forces :address-full-message-policy to be !:page for
  a :last-value-queue, since that's broken in HornetQ."
  [opts]
  (if (and (:last-value-queue opts)
        (= :page (:address-full-message-policy opts :page)))
    (assoc opts :address-full-message-policy :drop)
    opts))

(defn ^{:valid-options
        #{:address-full-message-policy :dead-letter-address :expiry-address
          :expiry-delay :last-value-queue :max-delivery-attempts :max-redelivery-delay
          :max-size-bytes :page-cache-max-size :page-size-bytes :redelivery-delay
          :redelivery-multiplier :redistribution-delay :send-to-dla-on-no-route}}
  set-address-options
  "Sets the HornetQ-specific address options for the given match.

   This provides programatic access to options that are normally set
   in the xml configuration. `match` must be either a destination
   returned from {{immutant.messaging/queue}} or
   {{immutant.messaging/topic}}, or a fully qualified jms destination
   name (prefixed with 'jms.queue.' or 'jms.topic.'). It may contain
   HornetQ wildcard matchers (see
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

   * :expiry-delay [-1] - If > -1, this value (in millis) is used as the
     default :ttl for messages that don't have a :ttl > 0 set.

   * :last-value-queue [false] - If true, only the most recent message
      for a last-value property will be retained. Setting this option will
      also cause :address-full-message-policy to be set to :drop, as HornetQ
      has a bug related to paging last value queues. See
      http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/last-value-queues.html

   * :max-delivery-attempts [10] - The number of times delivery will
     be attempted for a message before giving up. If :dead-letter-address
     is set, the message will be delivered there, or removed otherwise. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/undelivered-messages.html#undelivered-messages.configuring

   * :max-redelivery-delay [:redelivery-delay] - Specifies the maximum
     redelivery delay (in millis) when a :redelivery-multiplier is used.

   * :max-size-bytes [20971520 (20MB)] - The maximum size (in bytes) of retained messages
     on an address before :address-full-message-policy is applied. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :page-cache-max-size [5] - HornetQ will keep up to this many page files in
     memory to optimize IO. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :page-size-bytes [10485760 (10MB)] - The size (in bytes) of the page files created
     when paging. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/paging.html

   * :redelivery-delay [0] - Specifies the delay (in millis) between
     redelivery attempts. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/undelivered-messages.html#undelivered-messages.delay

   * :redelivery-multiplier [1.0] - Controls the backoff for redeliveries. The
     delay between redelivery attempts is calculated as
     :redelivery-delay * (:redelivery-multiplier ^ attempt-count). This won't have
     any effect if you don't also set :redelivery-delay and :max-redelivery-delay.

   * :redistribution-delay [1000] - Specifies the delay (in millis) to wait before
     redistributing messages from a node in a cluster to other nodes when the
     queue no longer has consumers on the current node. See
     http://docs.jboss.org/hornetq/2.3.0.Final/docs/user-manual/html/clusters.html

   * :send-to-dla-on-no-route [false] - If true, any message that can't be
     routed to its destination will be sent to :dead-letter-address.

   Calling this function again with the same match will override
   replace any previous settings for that match."
  [match settings]
  (o/validate-options settings set-address-options)
  (u/when-import 'org.hornetq.core.settings.impl.AddressSettings
    (when (seq settings)
      (.addAddressSettings
        (server-manager)
        (normalize-destination-match match)
        (reduce
          (fn [s [k v]]
            (doto s
              (pu/set-bean-property
                (k address-settings-aliases k)
                ((address-settings-coercions k identity) v))))
          (eval '(AddressSettings.))
          (set-companion-options settings))))))
