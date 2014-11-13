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

(ns immutant.messaging
  "Easily publish and receive messages containing any type of nested
   data structure to dynamically-created queues and topics."
  (:require [immutant.internal.options :as o]
            [immutant.internal.util    :as u]
            [immutant.codecs           :as codecs]
            [immutant.messaging.internal :refer :all])
  (:import [org.projectodd.wunderboss.messaging Context Destination
            Destination$ListenOption
            Destination$PublishOption
            Destination$ReceiveOption
            Message
            Messaging Messaging$CreateContextOption
            Messaging$CreateOption Messaging$CreateQueueOption
            Messaging$CreateTopicOption
            Queue Topic
            Topic$SubscribeOption Topic$UnsubscribeOption]))

(defn ^Context context
  "Creates a messaging context.

   A context represents a remote or local connection to the messaging
   broker.

   There are two reasons you would create a context rather than
   rely on the messaging functions to lazily create them as needed:

   1) for communicating with a remote HornetQ instance
   2) for sharing a context among a batch of messaging operations

   You are responsible for closing any contexts created via this
   function.

   Options that apply to both local and remote contexts are [default]:

   * :subscription-name - identifies the context for use with a durable topic subscriber [nil]
   * :xa?               - if true, returns an XA context for use in a distributed transaction [false]
   * :mode              - one of: :auto-ack, :client-ack, :transacted. Ignored if :xa is true. [:auto-ack]

   Options that apply to only remote contexts are [default]:

   * :host - the host of a remote broker [nil]
   * :port - the port of a remote broker [nil, 5445 if :host provided]
   * :username - a username for the remote broker [nil]
   * :password - the corresponding password for :username [nil]
   * :remote-type - when connecting to a HornetQ instance running
                    inside WildFly, this needs to be set to
                    :hornetq-wildfly [:hornetq-standalone]
   * :reconnect-attempts - total number of reconnect attempts to make
                           before giving up (-1 for unlimited) [0]
   * :reconnect-retry-interval - the period in milliseconds between subsequent
                                 recontext attempts [2000]
   * :reconnect-max-retry-interval - the max retry interval that will be used [2000]
   * :reconnect-retry-interval-multiplier - a multiplier to apply to the time
                                            since the last retry to compute the
                                            time to the next retry [1.0]"
  [& options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  coerce-context-mode
                  (o/validate-options context)
                  (as-> % (assoc % :client-id (:subscription-name %)))
                  (dissoc :subscription-name)
                  (update-in [:remote-type] o/->underscored-string))]
    (.createContext (broker nil)
      (o/extract-options options Messaging$CreateContextOption))))

(o/set-valid-options! context
  (-> (o/opts->set Messaging$CreateContextOption)
    (conj :subscription-name)
    (disj :client-id)
    (o/boolify :xa)))

(defn queue
  "Establishes a handle to a messaging queue.

   If given a :context, the context must be remote, and is remembered and
   used as a default option to any fn that takes a queue and a context.

   This creates the queue if no :context is provided and it does not
   yet exist.

   The following options are supported [default]:

   * :context - a context for a *remote* broker. Cannot be specified
     with any other options. [nil]

   Or:

   * :durable?   - whether messages persist across restarts [true]
   * :selector   - a JMS (SQL 92) expression to filter published messages [nil]"
  [queue-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options queue))]
    (queue-with-meta
      (.findOrCreateQueue (broker options) queue-name
        (o/extract-options options Messaging$CreateQueueOption))
      {:context (:context options)})))

(o/set-valid-options! queue
  (o/boolify (o/opts->set Messaging$CreateQueueOption) :durable))

(defn topic
  "Establishes a handle to a messaging topic.

   If given a :context, the context must be remote, and the context is
   remembered and used as a default option to any fn that takes a topic
   and a context.

   This creates the topic if no :context is provided and it does not
   yet exist.

   The following options are supported [default]:

   * :context - a context for a *remote* broker [nil]"
  [topic-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options topic))]
    (topic-with-meta
      (.findOrCreateTopic (broker options) topic-name
        (o/extract-options options Messaging$CreateTopicOption))
      {:context (:context options)})))

(o/set-valid-options! topic
  (o/opts->set Messaging$CreateTopicOption))

(defn publish
  "Send a message to a destination.

   If `message` has metadata, it will be transferred as headers
   and reconstituted upon receipt. Metadata keys must be valid Java
   identifiers (because they can be used in selectors) and can be overridden
   using the :properties option.

   If no context is provided, a new one is created for each call, which
   can be inefficient if you are sending a large number of messages.

   The following options are supported [default]:

     * :encoding    - one of: :edn, :json, :none, or other codec you've registered [:edn]
     * :priority    - 0-9, or one of: :low, :normal, :high, :critical [4]
     * :ttl         - time to live, in millis [0 (forever)]
     * :persistent? - whether undelivered messages survive restarts [true]
     * :properties  - a map to which selectors may be applied, overrides metadata [nil]
     * :context     - a context to use; caller expected to close [nil]"
  [^Destination destination message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context destination)
                  (o/validate-options publish)
                  (update-in [:properties] #(or % (meta message))))
        coerced-options (o/extract-options options Destination$PublishOption)]
    (.publish destination message (codecs/lookup-codec (:encoding options :edn))
      coerced-options)))

(o/set-valid-options! publish
  (-> (o/opts->set Destination$PublishOption)
    (conj :encoding)
    (o/boolify :persistent)))

(defn receive
  "Receive a message from `destination`.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression may be received.

   If no context is provided, a new one is created for each call, which
   can be inefficient if you are receiving a large number of messages.

   The following options are supported [default]:

     * :timeout      - time in millis, after which the timeout-val is returned. 0
                       means wait forever, -1 means don't wait at all [10000]
     * :timeout-val  - the value to return when a timeout occurs. Also returned when
                       a timeout of -1 is specified, and no message is available [nil]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is returned. Otherwise, the
                       base message object is returned [true]
     * :context      - a context to use; caller expected to close [nil]"
  [^Destination destination & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context destination)
                  (o/validate-options receive))
        ^Message message (.receive destination codecs/codecs
                           (o/extract-options options Destination$ReceiveOption))]
    (if message
      (if (:decode? options true)
        (decode-with-metadata message)
        message)
      (:timeout-val options))))

(o/set-valid-options! receive
  (-> (o/opts->set Destination$ReceiveOption)
    (conj :encoding :timeout-val)
    (o/boolify :decode)))

(defn listen
  "Registers `f` to receive each message sent to `destination`.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression will be received.

   If given a :context, the context must be remote, and the mode of that
   context is ignored, since it is used solely to generate sub-contexts
   for each listener thread. Closing the given context will also close
   the listener.

   The following options are supported [default]:

     * :concurrency  - the number of threads handling messages [1]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is passed to `f`. Otherwise, the
                       base message object is passed [true]
     * :context      - a context for a *remote* broker; caller expected to close [nil]
     * :mode         - the mode to use for the listener context. One of :auto-ack, :client-ack,
                       :transacted [:transacted]

   Returns a listener object that can be stopped by passing it to [[stop]], or by
   calling .close on it."
  [^Destination destination f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context destination)
                  coerce-context-mode
                  (o/validate-options listen))]
    (.listen destination
      (message-handler f (:decode? options true))
      codecs/codecs
      (o/extract-options options Destination$ListenOption))))

(o/set-valid-options! listen
  (o/boolify (o/opts->set Destination$ListenOption) :decode))

(defn request
  "Send `message` to `queue` and return a Future that will retrieve the response.

   Implements the request-response pattern, and is used in conjunction
   with [[respond]].

   It takes the same options as [[publish]]."
  [^Queue queue message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context queue)
                  (o/validate-options publish)
                  (update-in [:properties] #(or % (meta message))))]
    (delegating-future
      (.request queue message
                 (codecs/lookup-codec (:encoding options :edn))
                 codecs/codecs
                 (o/extract-options options Destination$PublishOption))
      decode-with-metadata)))

(defn respond
  "Listen for messages on `queue` sent by the [[request]] function and
   respond with the result of applying `f` to the message.

   Accepts the same options as [[listen]], along with [default]:

     * :ttl  - time for the response mesage to live, in millis [60000 (1 minute)]"
  [^Queue queue f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context queue)
                  coerce-context-mode
                  (o/validate-options respond))]
    (.respond queue
      (message-handler f (:decode? options true) true)
      codecs/codecs
      (o/extract-options options Destination$ListenOption))))

(o/set-valid-options! respond (conj (o/valid-options-for listen)
                                :ttl))

(defn subscribe
  "Sets up a durable subscription to `topic`, and registers a listener with `f`.

   `subscription-name` is used to identify the subscription, allowing
   you to stop the listener and resubscribe with the same name in the
   future without losing messages sent in the interim.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression may be received.

   If no context is provided, a new context is created for this
   subscriber. If a context is provided, it must have
   its :subscription-name set as well.

   The following options are supported [default]:

     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is passed to `f`. Otherwise, the
                       javax.jms.Message object is passed [true]
     * :context      - a context to use; caller expected to close [nil]

   Returns a listener object that can can be stopped by passing it to [[stop]], or by
   calling .close on it.

   Subscriptions should be torn down when no longer needed - see [[unsubscribe]]."
  [^Topic topic subscription-name f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context topic)
                  (o/validate-options listen subscribe))]
    (.subscribe topic (name subscription-name)
      (message-handler f (:decode? options true))
      codecs/codecs
      (o/extract-options options Topic$SubscribeOption))))

(o/set-valid-options! subscribe
  (o/boolify (o/opts->set Topic$SubscribeOption) :decode))

(defn unsubscribe
  "Tears down the durable topic subscription on `topic` named `subscription-name`.

   If no context is provided, a new context is created for this
   action. If a context is provided, it must have its :subscription-name
   set to the same value used when creating the subscription. See
   [[subscribe]].

   The following options are supported [default]:

     * :context   - a context to use; caller expected to close [nil]"
  [^Topic topic subscription-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-context topic)
                  (o/validate-options unsubscribe))]
    (.unsubscribe topic (name subscription-name)
      (o/extract-options options Topic$UnsubscribeOption))))

(o/set-valid-options! unsubscribe
  (o/opts->set Topic$UnsubscribeOption))

(defn stop
  "Stops the given context, destination, listener, or subscription listener.

   Note that stopping a destination may remove it from the broker if
   called outside of the container."
  [x]
  (if (instance? Destination x)
    (.stop x)
    (.close x)))
