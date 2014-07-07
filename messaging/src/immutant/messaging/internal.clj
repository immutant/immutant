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

(ns ^:no-doc immutant.messaging.internal
    (:require [immutant.internal.options :as o]
              [immutant.internal.util    :as u])
    (:import java.lang.AutoCloseable
             org.projectodd.wunderboss.WunderBoss
             [org.projectodd.wunderboss.messaging
              ConcreteReply Connection
              Message
              Messaging Messaging$CreateOption
              Messaging$CreateConnectionOption
              MessageHandler ReplyableMessage
              Session$Mode]))

(def ^:internal create-defaults (o/opts->defaults-map Messaging$CreateOption))

(def broker-name
  (partial u/hash-based-component-name create-defaults))

(defn broker [opts]
  (WunderBoss/findOrCreateComponent Messaging
    (broker-name (select-keys opts (o/opts->set Messaging$CreateOption)))
    (o/extract-options opts Messaging$CreateOption)))

(defn delegating-future [future result-fn]
  (reify
    java.util.concurrent.Future
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

(defn message-handler [f decode?]
  (let [bound-f (bound-fn [m] (f m))]
    (reify MessageHandler
      (onMessage [_ message _]
        (let [reply (bound-f (if decode?
                               (decode-with-metadata message)
                               message))]
          (ConcreteReply. reply (meta reply)))))))

(defn coerce-session-mode [mode]
  (case mode
    nil         Session$Mode/AUTO_ACK
    :auto-ack   Session$Mode/AUTO_ACK
    :client-ack Session$Mode/CLIENT_ACK
    :transacted Session$Mode/TRANSACTED
    (throw (IllegalArgumentException.
             (str mode " is not a valid session mode")))))

(defn merge-connection [opts x]
  (if (= :session x)
    (update-in opts [:connection]
      #(or % (when-let [s (:session opts)]
               (.connection s))))
    (update-in opts [:connection] #(or % (:connection x)))))
