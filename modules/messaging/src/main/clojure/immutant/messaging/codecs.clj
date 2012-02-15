;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.messaging.codecs
  (:require [immutant.codecs :as core]))

(def encoding-header-name "__ContentEncoding__")

(defn ^{:private true} set-encoding [^javax.jms.Message msg enc]
  (.setStringProperty msg encoding-header-name (name enc))
  msg)

(defn ^{:private true} get-encoding [^javax.jms.Message msg]
  (keyword (.getStringProperty msg encoding-header-name)))

;; Encode

(defmulti encode (fn [_ _ {encoding :encoding}] (or encoding :clojure)))

(defmethod encode :clojure [session message options]
  "Stringify a clojure data structure"
  (set-encoding
   (.createTextMessage session (core/encode message))
   :clojure))

(defmethod encode :json [session message options]
  "Stringify a json data structure"
  (set-encoding
   (.createTextMessage session (core/encode message :json))
   :json))

(defmethod encode :text [session message options]
  "Treat the payload as a raw String. No encoding is done."
  (set-encoding
   (.createTextMessage session message)
   :text))

;; Decode

(defmulti decode (fn [message] (get-encoding message)))

(defmethod decode :clojure [message]
  "Turn a string into a clojure data structure"
  (core/decode (.getText message)))

(defmethod decode :json [message]
  "Turn a string into a json data structure"
  (core/decode (.getText message) :json))

(defmethod decode :text [message]
  "Treats the message payload as a raw string. No decoding is done."
  (.getText message))

(defmethod decode :default [message]
  (throw (RuntimeException. (str "Received unknown message encoding: " (get-encoding message)))))
