;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns ^{:no-doc true} immutant.messaging.codecs
  (:require [immutant.codecs :as core])
  (:use [immutant.messaging.core :only [get-properties]])
  (:import [javax.jms BytesMessage TextMessage]))

(def encoding-header-name "__ContentEncoding__")

(defn ^{:private true} set-encoding [^javax.jms.Message msg enc]
  (.setStringProperty msg encoding-header-name (name enc))
  msg)

(defn get-encoding
  "Retrieve the encoding from a JMS message."
  [^javax.jms.Message msg]
  (keyword (.getStringProperty msg encoding-header-name)))

(defprotocol AsText
  "Function for extracting text from a message."
  (message-text [message]))

(extend-type BytesMessage
  AsText
  (message-text [message]
    (let [bytes (byte-array (.getBodyLength message))]
      (.readBytes message bytes)
      (String. bytes))))

(extend-type TextMessage
  AsText
  (message-text [message]
    (.getText message)))

;; Encode

(defmulti encode (fn [_ _ {encoding :encoding}] (or encoding :edn)))

(defmethod encode :clojure [^javax.jms.Session session message options]
  "Stringify a clojure data structure"
  (set-encoding
   (.createTextMessage session (core/encode message))
   :clojure))

(defmethod encode :edn [^javax.jms.Session session message options]
  "Stringify an edn data structure"
  (set-encoding
   (.createTextMessage session (core/encode message :edn))
   :edn))

(defmethod encode :json [^javax.jms.Session session message options]
  "Stringify a json data structure"
  (set-encoding
   (.createTextMessage session (core/encode message :json))
   :json))

(defmethod encode :text [^javax.jms.Session session message options]
  "Treat the payload as a raw String. No encoding is done."
  (set-encoding
   (.createTextMessage session message)
   :text))

;; Decode

(defmulti decode (fn [message] (or (get-encoding message) :text)))

(defmethod decode :clojure [message]
  "Turn a string into a clojure data structure"
  (core/decode (message-text message)))

(defmethod decode :edn [message]
  "Turn a string into an edn data structure"
  (core/decode (message-text message) :edn))

(defmethod decode :json [message]
  "Turn a string into a json data structure"
  (core/decode (message-text message) :json))

(defmethod decode :text [message]
  "Treats the message payload as a raw string. No decoding is done."
  (message-text message))

(defmethod decode :default [message]
  (throw (RuntimeException. (str "Received unknown message encoding: " (get-encoding message)))))

(defn decode-with-metadata
  "Decodes the given message. If the decoded message is a clojure
   collection, the properties of the message will be affixed as
   metadata"
  [msg]
  (let [result (decode msg)]
    (if (instance? clojure.lang.IObj result)
      (with-meta result (get-properties msg))
      result)))

(defn decode-if
  "Decodes the given message if decode? is truthy"
  [decode? msg]
  (if decode?
    (binding [*read-eval* false] (decode-with-metadata msg))
    msg))
