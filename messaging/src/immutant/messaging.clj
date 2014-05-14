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
   data structure to dynamically-created endpoints."
  (:require [immutant.internal.options :as o]
            [immutant.internal.util    :as u]
            [immutant.messaging.codecs :as codecs]
            [immutant.messaging.internal :refer :all])
  (:import [org.projectodd.wunderboss.messaging Connection
            Connection$SendOption Connection$ListenOption
            Connection$ReceiveOption
            Endpoint Messaging
            Messaging$CreateConnectionOption
            Messaging$CreateEndpointOption Messaging$CreateOption
            Messaging$CreateSubscriptionOption]))

(defn endpoint
  "Establishes a handle to a messaging endpoint.

   Options are [default]:

   * :broadcast? - If true, this is a broadcast endpoint (aka a JMS
      Topic).  Otherwise, it's a queue-based endpoint. [false]

   The following options are supported for non-broadcast endpoints only [default]:

   * :durable? - whether messages persist across restarts [true]
   * :selector - a JMS (SQL 92) expression to filter published messages [nil]

   This creates the endpoint if necessary."
  [endpoint-name & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options endpoint))]
    (.findOrCreateEndpoint (broker options) endpoint-name
      (o/extract-options options Messaging$CreateEndpointOption))))

(o/set-valid-options! endpoint
  (conj (o/opts->set Messaging$CreateEndpointOption) :broadcast? :durable?))

(defn ^Connection connection
  "Creates a connection to the messaging broker.

   Options are [default]:

   * :subscription - identifies a durable topic subscriber, ignored for queues [nil]

   TODO: more docs"
  [& options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options connection))]
    (connection* options)))

(o/set-valid-options! connection
  (o/opts->set Messaging$CreateConnectionOption))

(defn ^:internal ^:no-doc with-connection
  "If :connection is in `options`, `f` is called with that
  connection. If a connection isn't available, one is created, `f` is
  called with it, then the connection is closed."
  [options f]
  (if-let [connection (:connection options)]
    (f connection)
    (with-open [connection (connection options)]
      (f connection))))

(defn publish
  "Send a message to an endpoint.

   If `message` has metadata, it will be transferred as headers
   and reconstituted upon receipt. Metadata keys must be valid Java
   identifiers (because they can be used in selectors) and can be overridden
   using the :properties option.

   The following options are supported [default]:

     * :encoding   - one of: :clojure, :edn, :fressian, :json, :text [:edn]
     * :priority   - 0-9, or one of: :low, :normal, :high, :critical [4]
     * :ttl        - time to live, in ms [0 (forever)]
     * :persistent - whether undelivered messages survive restarts [true]
     * :properties - a map to which selectors may be applied, overrides metadata [nil]
     * :connection - a connection to use; caller expected to close [nil]"
  [^Endpoint endpoint message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options publish)
                  (update-in [:properties] (fn [p] (or p (meta message)))))
        [msg ^String content-type] (codecs/encode message (:encoding options :edn))
        coerced-options (o/extract-options options Connection$SendOption)]
    (with-connection options
      (fn [^Connection connection]
        (if (instance? String msg)
          (.send connection endpoint ^String msg content-type
            coerced-options)
          (.send connection endpoint ^"bytes" msg content-type
            coerced-options))))))

(o/set-valid-options! publish
  (-> (o/opts->set Connection$SendOption)
    (concat (o/valid-options-for connection))
    set
    (conj :connection :encoding)))

(defn subscribe
  "Creates a durable subscription.

   TODO: more docs"
  ([endpoint subscription-name]
     (subscribe endpoint subscription-name nil))
  ([endpoint subscription-name selector]
     (.createSubscription (broker {}) endpoint
       subscription-name
       {Messaging$CreateSubscriptionOption/SELECTOR selector})))

(defn receive
  "Receive a message from an endpoint.

   If a :selector is provided, then only having metadata/headers matching
   that expression may be received.

   The following options are supported [default]:

     * :timeout      - time in ms, after which the timeout-val is returned. 0
                       means wait forever, -1 means don't wait at all [10000]
     * :timeout-val  - the value to return when a timeout occurs. Also returned when
                       a timeout of -1 is specified, and no message is available [nil]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is returned. Otherwise, the
                       base message object is returned [true]
     * :subscription - identifies a durable topic subscriber, ignored for queues [nil]
     * :connection   - a connection to use; caller expected to close [nil]"
  [^Endpoint endpoint & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options receive))
        ^Message message (with-connection options
                           (fn [^Connection c]
                             (.receive c endpoint
                               (o/extract-options options Connection$ReceiveOption))))]
    (if message
      (if (:decode? options true)
        (codecs/decode message)
        message)
      (:timeout-val options))))

(o/set-valid-options! receive
  (-> (o/opts->set Connection$ReceiveOption)
    (concat (o/valid-options-for connection))
    set
    (conj :connection :decode? :encoding :timeout-val)))

(defn listen
  "The handler function, f, will receive each message sent to endpoint.

   If a :selector is provided, then only messages having
   metadata/properties matching that expression may be received.

   The following options are supported [default]:

     * :concurrency  - the number of threads handling messages [1]
     * :xa           - Whether the handler demarcates an XA transaction [true]
     * :selector     - A JMS (SQL 92) expression matching message metadata/properties [nil]
     * :decode?      - if true, the decoded message body is passed to `f`. Otherwise, the
                       javax.jms.Message object is passed [true]
     * :subscription - identifies a durable topic subscriber, ignored for queues [nil]

   Returns a listener object that can can be stopped by passing it to {{stop}}, or by
   calling .close on it."
  [^Endpoint endpoint f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options listen))
        connection (connection options)
        listener (.listen connection endpoint
                   (message-handler
                     #(f (if (:decode? options true)
                           (codecs/decode %)
                           %)))
                   (o/extract-options options Connection$ListenOption))]
    (multi-closer listener connection)))

(o/set-valid-options! listen
  (-> (o/opts->set Connection$ListenOption)
    (concat (o/valid-options-for connection))
    set
    (conj :decode?)))

(defn request
  "Send `message` to `endpoint` and return a Future that will retrieve the response.

   Implements the request-response pattern, and is used in conjunction
   with {{respond}}. endpoint must be a non-broadcast endpoint.

   It takes the same options as {{publish}}."
  [^Endpoint endpoint message & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options publish)
                  (update-in [:properties] (fn [p] (or p (meta message)))))
        [msg ^String content-type] (codecs/encode message (:encoding options :edn))
        coerced-options (o/extract-options options Connection$SendOption)
        future (with-connection options
                 (fn [^Connection connection]
                   (if (instance? String msg)
                     (.request connection endpoint ^String msg content-type
                       coerced-options)
                     (.request connection endpoint ^"bytes" msg content-type
                       coerced-options))))]
    (delegating-future future codecs/decode)))

(defn respond
  "Listen for messages on `endpoint` sent by the {{request}} function and
   respond with the result of applying `f` to the message.

   `endpoint` must be a non-broadcast endpoint.

   Accepts the same options as {{listen}}, along with [default]:

     * :ttl  - time for the response mesage to live, in ms [60000 (1 minute)]"
  [^Endpoint endpoint f & options]
  (let [options (-> options
                  u/kwargs-or-map->map
                  (o/validate-options respond))
        connection (connection options)
        listener (.respond connection endpoint
                   (response-handler f options)
                   (o/extract-options options Connection$ListenOption))]
    (multi-closer listener connection)))

(o/set-valid-options! respond (conj (o/valid-options-for listen)
                                :ttl))

(defn stop
  "Stops the given connection, listener, endpoint, or subscription."
  [x]
  (.close x))
