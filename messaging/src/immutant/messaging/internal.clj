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
             [org.projectodd.wunderboss.messaging Connection
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

(defn message-handler [f decode?]
  (let [bound-f (bound-fn [m] (f m))]
    (reify MessageHandler
      (onMessage [_ message _]
        (bound-f (if decode?
                   (.body message)
                   message))))))

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
