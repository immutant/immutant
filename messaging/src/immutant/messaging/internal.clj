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
              [immutant.internal.util    :as u]
              [immutant.messaging.codecs :as codecs]
              [immutant.codecs           :as core-codecs])
    (:import org.projectodd.wunderboss.WunderBoss
             [org.projectodd.wunderboss.messaging Connection
              Connection$SendOption
              Messaging Messaging$CreateOption
              Messaging$CreateConnectionOption
              MessageHandler ReplyableMessage]))

(def ^:internal create-defaults (o/opts->defaults-map Messaging$CreateOption))

(def broker-name
  (partial u/hash-based-component-name create-defaults))

(defn broker [opts]
  (doto
      (WunderBoss/findOrCreateComponent Messaging
        (broker-name (select-keys opts (o/opts->set Messaging$CreateOption)))
        (o/extract-options opts Messaging$CreateOption))
    .start))

(defn multi-closer [& xs]
  (reify java.lang.AutoCloseable
    (close [_]
      (doseq [x xs]
        (.close x)))))

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

(defn message-handler [f]
  (let [bound-f (bound-fn [m] (f m))]
    (reify MessageHandler
      (onMessage [_ message]
        (bound-f message)))))

(defn response-handler [f options]
  (message-handler
    (fn [message]
      (first
        (codecs/encode
          (f
            (if (:decode? options true)
              (codecs/decode message)
              message))
          (core-codecs/content-type->encoding (.contentType message)))))))

(defn connection* [options]
    (.createConnection (broker options)
      (o/extract-options options Messaging$CreateConnectionOption)))

(defrecord Subscription [client-id subscriber-name]
  java.lang.AutoCloseable
  (close [_]
    ()))
