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
            [immutant.messaging.codecs :as codecs]
            [immutant.messaging.internal :refer :all])
  (:import [org.projectodd.wunderboss.messaging Connection Destination
            Connection$CreateSessionOption
            Destination$SendOption Destination$ListenOption
            Destination$ReceiveOption
            Messaging Messaging$CreateConnectionOption
            Messaging$CreateOption Messaging$CreateQueueOption
            Messaging$CreateTopicOption
            Queue Topic
            Topic$SubscribeOption Topic$UnsubscribeOption
            Session]))

(defn queue
  "Establishes a handle to a messaging queue.

   The following options are supported [default]:

   * :connection - a connection to a remote broker [nil]
   * :durable?   - whether messages persist across restarts [true]
   * :selector   - a JMS (SQL 92) expression to filter published messages [nil]

   If given a :connection, the connection is remembered and used as a
   default option to any fn that takes a queue and a connection.

   This creates the queue if no :connection is provided and it does not
   yet exist."
  [queue-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options queue))]
    {:destination
     (.findOrCreateQueue (broker options) queue-name
       (o/extract-options options Messaging$CreateQueueOption)),
     :connection (:connection options)}))

(o/set-valid-options! queue
  (conj (o/opts->set Messaging$CreateQueueOption) :durable?))

(defn topic
  "Establishes a handle to a messaging topic.

   The following options are supported [default]:

   * :connection - a connection to a remote broker [nil]

   If given a :connection, the connection is remembered and used as a
   default option to any fn that takes a topic and a connection.

   This creates the topic if no :connection is provided and it does not
   yet exist."
  [topic-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options topic))]
    {:destination
     (.findOrCreateTopic (broker options) topic-name
       (o/extract-options options Messaging$CreateTopicOption)),
     :connection (:connection options)}))

(o/set-valid-options! topic
  (o/opts->set Messaging$CreateTopicOption))

(defn ^Connection connection
  "Creates a connection to the messaging broker.

   You are responsible for closing any connection created via this
   function.

   Options that apply to both local and remote connections are [default]:

   * :client-id - identifies the client id for use with a durable topic subscriber [nil]

   Options that apply to only remote connections are [default]:

   * :host - the host of a remote broker [nil]
   * :port - the port of a remote broker [nil, 5445 if :host provided]
   * :reconnect-attempts - total number of reconnect attempts to make
                           before giving up (-1 for unlimited) [0]
   * :reconnect-retry-interval - the period in milliseconds between subsequent
                                 reconnection attempts [2000]
   * :reconnect-max-retry-interval - the max retry interval that will be used [2000]
   * :reconnect-retry-interval-multiplier - a multiplier to apply to the time
                                            since the last retry to compute the
                                            time to the next retry [1.0]"
  [& options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options connection))]
    (.createConnection (broker nil)
      (o/extract-options options Messaging$CreateConnectionOption))))

(o/set-valid-options! connection
  (o/opts->set Messaging$CreateConnectionOption))

(defn ^Session session
  "Creates a session from the given `connection`.

   If no connection is provided, the default shared connection is
   used.

   The following options are supported [default]:

     * :mode       - one of: :auto-ack, :client-ack, :transacted [:auto-ack]
     * :connection - a connection to use; caller expected to close [nil]

   If given a :connection, the connection is remembered and used as a
   default option to any fn that takes a session and a connection.

   You are responsible for closing any sessions created via this
   function.

   TODO: more docs/examples"
  [& options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (update-in [:mode] coerce-session-mode)
                  (o/validate-options session))]
    (.createSession (or (:connection options) (.defaultConnection (broker nil)))
      (o/extract-options options Connection$CreateSessionOption))))

(o/set-valid-options! session
  (conj (o/opts->set Connection$CreateSessionOption)
    :connection))

(defn publish
  "Send a message to a destination.

   If `message` has metadata, it will be transferred as headers
   and reconstituted upon receipt. Metadata keys must be valid Java
   identifiers (because they can be used in selectors) and can be overridden
   using the :properties option.

   If no connection is provided, the default shared connection is
   used. If no session is provided, a new one is opened and closed.

   The following options are supported [default]:

     * :encoding   - one of: :clojure, :edn, :fressian, :json, :none [:edn]
     * :priority   - 0-9, or one of: :low, :normal, :high, :critical [4]
     * :ttl        - time to live, in millis [0 (forever)]
     * :persistent - whether undelivered messages survive restarts [true]
     * :properties - a map to which selectors may be applied, overrides metadata [nil]
     * :connection - a connection to use; caller expected to close [nil]
     * :session    - a session to use; caller expected to close [nil]"
  [destination message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection :session)
                  (merge-connection destination)
                  (o/validate-options publish)
                  (update-in [:properties] #(or % (meta message))))
        [msg ^String content-type] (codecs/encode message (:encoding options :edn))
        coerced-options (o/extract-options options Destination$SendOption)
        ^Destination dest (:destination destination)]
    (if (instance? String msg)
      (.send dest ^String msg content-type coerced-options)
      (.send dest ^"bytes" msg content-type coerced-options))))

(o/set-valid-options! publish
  (conj (o/opts->set Destination$SendOption)
    :encoding))

(defn receive
  "Receive a message from `destination`.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression may be received.

   If no connection is provided, the default shared connection is
   used. If no session is provided, a new one is opened and closed.

   The following options are supported [default]:

     * :timeout      - time in millis, after which the timeout-val is returned. 0
                       means wait forever, -1 means don't wait at all [10000]
     * :timeout-val  - the value to return when a timeout occurs. Also returned when
                       a timeout of -1 is specified, and no message is available [nil]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is returned. Otherwise, the
                       base message object is returned [true]
     * :connection   - a connection to use; caller expected to close [nil]
     * :session      - a session to use; caller expected to close [nil]"
  [destination & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection :session)
                  (merge-connection destination)
                  (o/validate-options receive))
        ^Message message (.receive ^Destination (:destination destination)
                           (o/extract-options options Destination$ReceiveOption))]
    (if message
      (if (:decode? options true)
        (codecs/decode message)
        message)
      (:timeout-val options))))

(o/set-valid-options! receive
  (conj (o/opts->set Destination$ReceiveOption)
    :decode? :encoding :timeout-val))

(defn listen
  "Registers `f` to receive each message sent to `destination`.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression will be received.

   If no connection is provided, the default shared connection is
   used.

   The following options are supported [default]:

     * :concurrency  - the number of threads handling messages [1]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is passed to `f`. Otherwise, the
                       base message object is passed [true]
     * :connection   - a connection to use; caller expected to close [nil]

   Returns a listener object that can be stopped by passing it to {{stop}}, or by
   calling .close on it."
  [destination f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection destination)
                  (o/validate-options listen))]
    (.listen ^Destination (:destination destination)
      (message-handler
        #(f (if (:decode? options true)
              (codecs/decode %)
              %)))
      (o/extract-options options Destination$ListenOption))))

(o/set-valid-options! listen
  (conj (o/opts->set Destination$ListenOption) :decode?))

(defn request
  "Send `message` to `queue` and return a Future that will retrieve the response.

   Implements the request-response pattern, and is used in conjunction
   with {{respond}}.

   It takes the same options as {{publish}}."
  [queue message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection :session)
                  (merge-connection queue)
                  (o/validate-options publish)
                  (update-in [:properties] (fn [p] (or p (meta message)))))
        [msg ^String content-type] (codecs/encode message (:encoding options :edn))
        coerced-options (o/extract-options options Destination$SendOption)
        ^Queue q (:destination queue)
        future (if (instance? String msg)
                 (.request q ^String msg content-type coerced-options)
                 (.request q ^"bytes" msg content-type coerced-options))]
    (delegating-future future codecs/decode)))

(defn respond
  "Listen for messages on `queue` sent by the {{request}} function and
   respond with the result of applying `f` to the message.

   Accepts the same options as {{listen}}, along with [default]:

     * :ttl  - time for the response mesage to live, in millis [60000 (1 minute)]"
  [queue f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection queue)
                  (o/validate-options respond))]
    (.respond ^Queue (:destination queue)
      (response-handler f options)
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

   If no connection is provided, a new connection is created for this
   subscriber. If a connection is provided, it must have its :client-id
   set.

   The following options are supported [default]:

     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is passed to `f`. Otherwise, the
                       javax.jms.Message object is passed [true]
     * :connection   - a connection to use; caller expected to close [nil]

   Returns a listener object that can can be stopped by passing it to {{stop}}, or by
   calling .close on it.

   Subscriptions should be torn down when no longer needed - see {{unsubscribe}}."
  [topic subscription-name f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection topic)
                  (o/validate-options listen subscribe))]
    (.subscribe ^Topic (:destination topic) (name subscription-name)
      (message-handler
        #(f (if (:decode? options true)
              (codecs/decode %)
              %)))
      (o/extract-options options Topic$UnsubscribeOption))))

(o/set-valid-options! subscribe
  (conj (o/opts->set Topic$SubscribeOption) :decode?))

(defn unsubscribe
  "Tears down the durable topic subscription on `topic` named `subscription-name`.

   If no connection is provided, a new connection is created for this
   action. If a connection is provided, it must have its :client-id set
   to the same value used when creating the subscription. See
   {{subscribe}}.

   The following options are supported [default]:

     * :connection   - a connection to use; caller expected to close [nil]"
  [topic subscription-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (merge-connection topic)
                  (o/validate-options unsubscribe))]
    (.unsubscribe ^Topic (:destination topic) (name subscription-name)
      (o/extract-options options Topic$UnsubscribeOption))))

(o/set-valid-options! unsubscribe
  (o/opts->set Topic$UnsubscribeOption))

(defn stop
  "Stops the given connection, destination, listener, session, or subscription listener.

   Note that stopping a destination may remove it from the broker if
   called outside of the container."
  [x]
  (let [x (:destination x (:session x x))]
    (if (instance? Destination x)
      (.stop x)
      (.close x))))
