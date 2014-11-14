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

(ns ^:no-doc immutant.messaging.internal
    (:require [immutant.internal.options :as o]
              [immutant.internal.util    :as u])
    (:import java.lang.AutoCloseable
             java.util.concurrent.Future
             org.projectodd.wunderboss.WunderBoss
             [org.projectodd.wunderboss.messaging
              ConcreteReply
              Context Context$Mode
              Message
              Messaging Messaging$CreateOption
              Messaging$CreateContextOption
              MessageHandler ReplyableMessage
              Queue Topic]))

(def ^:internal create-defaults (o/opts->defaults-map Messaging$CreateOption))

(def broker-name
  (partial u/hash-based-component-name create-defaults))

(defn ^Messaging broker [opts]
  (WunderBoss/findOrCreateComponent Messaging
    (broker-name (select-keys opts (o/opts->set Messaging$CreateOption)))
    (o/extract-options opts Messaging$CreateOption)))

(defn queue-with-meta [^Queue queue meta]
  (with-meta
    (reify Queue
      (listen [_ handler codecs options]
        (.listen queue handler codecs options))
      (name [_]
        (.name queue))
      (publish [_ content codec options]
        (.publish queue content codec options))
      (receive [_ codecs options]
        (.receive queue codecs options))
      (request [_ content codec codecs options]
        (.request queue content codec codecs options))
      (respond [_ handler codecs options]
        (.respond queue handler codecs options))
      (stop [_]
        (.stop queue)))
    (assoc meta :wrapped-destination queue)))

(defn topic-with-meta [^Topic topic meta]
  (with-meta
    (reify Topic
      (listen [_ handler codecs options]
        (.listen topic handler codecs options))
      (name [_]
        (.name topic))
      (publish [_ content codec options]
        (.publish topic content codec options))
      (receive [_ codecs options]
        (.receive topic codecs options))
      (stop [_]
        (.stop topic))
      (subscribe [_ id handler codecs options]
        (.subscribe topic id handler codecs options))
      (unsubscribe [_ id options]
        (.unsubscribe topic id options)))
    (assoc meta :wrapped-destination topic)))

(defn delegating-future [^Future future result-fn]
  (reify
    Future
    (cancel [_ interrupt?]
      (.cancel future interrupt?))
    (isCancelled [_]
      (.isCancelled future))
    (isDone [_]
      (.isDone future))
    (get [_]
      (result-fn (.get future)))
    (get [_ timeout unit]
      (result-fn (.get future timeout unit)))))

(defn decode-with-metadata
  "Decodes the given message. If the decoded message is a clojure
   collection, the properties of the message will be affixed as
   metadata"
  [^Message msg]
  (let [result (.body msg)]
    (if (instance? clojure.lang.IObj result)
      (with-meta result (dissoc (into {} (.properties msg))
                          "JMSXDeliveryCount"
                          "contentType"
                          "synchronous"
                          "synchronous_response"
                          "sync_request_node_id"
                          "sync_request_id"))
      result)))

(defn message-handler
  ([f decode?]
     (message-handler f decode? false))
  ([f decode? reply?]
     (let [loader (clojure.lang.RT/baseLoader)
           bound-f (bound-fn [m] (f m))]
       (reify MessageHandler
         (onMessage [_ message _]
           ;; use baseLoader as the tccl to allow records to be
           ;; decoded, since HornetQ will use the tccl to load the
           ;; record class
           (u/with-tccl loader
             (let [reply (bound-f (if decode?
                                    (decode-with-metadata message)
                                    message))]
               (when reply? (ConcreteReply. reply (meta reply))))))))))

(defn coerce-context-mode [opts]
  (if-let [mode (:mode opts)]
    (assoc opts
      :mode (case mode
              :auto-ack   Context$Mode/AUTO_ACK
              :client-ack Context$Mode/CLIENT_ACK
              :transacted Context$Mode/TRANSACTED
              (throw (IllegalArgumentException.
                       (str mode " is not a valid context mode")))))
    opts))

(defn merge-context [opts x]
  (update-in opts [:context] #(or % (:context (meta x)))))
