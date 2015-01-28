;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.sse
  "Provides Server-Sent Events via [[immutant.web.async/as-channel]]"
  (:require [immutant.web.async :as async]))

(defn ^:no-doc ^:internal with-event-type [m]
  (update-in m [:headers] assoc "Content-Type" "text/event-stream; charset=utf-8"))

(defprotocol Event
  (event->str [x] "Formats event according to SSE spec"))

(extend-protocol Event
  nil
  (event->str [_] "data:\n")
  Object
  (event->str [o] (str "data:" o "\n"))
  java.util.Collection
  (event->str [c] (apply str (map event->str c)))
  java.util.Map
  (event->str [m] (apply str
                (-> (for [[k v] (dissoc m :data)]
                      (format "%s:%s\n" (name k) v))
                  (conj (event->str (:data m)))))))

(def as-channel
  "Decorates the result of [[immutant.web.async/as-channel]] with the
  proper SSE header"
  (comp with-event-type async/as-channel))

(defn send!
  "Formats an event according to the SSE spec and sends it
  via [[immutant.web.async/send!]] with an optional on-complete
  callback"
  ([ch event]
   (async/send! ch (str (event->str event) "\n")))
  ([ch event on-complete]
   (async/send! ch (str (event->str event) "\n") :on-complete on-complete)))
