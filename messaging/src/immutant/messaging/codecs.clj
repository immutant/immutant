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

(ns ^:no-doc immutant.messaging.codecs
  (:require [immutant.codecs :as core])
  (:import org.projectodd.wunderboss.messaging.Message))

;; Encode

(defmulti encode (fn [_ {encoding :encoding}] encoding))

(defmethod encode :default [message {:keys [encoding] :or {encoding :edn}}]
  [(core/encode message encoding)
   (core/encoding->content-type encoding)])

(defmethod encode :fressian [message _]
  (let [data (core/encode message :fressian)
        bytes (byte-array (.remaining data))]
    [(.get data bytes)
     (core/encoding->content-type :fressian)]))

(defmethod encode :text [message _]
  [(core/encode message :raw)
   (core/encoding->content-type :text)])

;; Decode

(defmulti decode (fn [^Message message]
                   (core/content-type->encoding (.contentType message))))

(defmethod decode :default [^Message message]
  (core/decode (.body message String)
    (core/content-type->encoding (.contentType message))))

(defmethod decode :fressian [^Message message]
  (core/decode (.body message (Class/forName "[B")) :fressian))

(defn decode-with-metadata
  "Decodes the given message. If the decoded message is a clojure
   collection, the properties of the message will be affixed as
   metadata"
  [^Message msg]
  (let [result (decode msg)]
    (if (instance? clojure.lang.IObj result)
      (with-meta result (into {} (.properties msg)))
      result)))
