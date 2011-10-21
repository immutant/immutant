;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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
  (:require [clojure.data.json :as json]))

(def encoding-header-name "__ContentEncoding__")

(defn ^:private set-encoding [^javax.jms.Message msg enc]
  (.setStringProperty msg encoding-header-name (name enc))
  msg)

(defn ^:private get-encoding [^javax.jms.Message msg]
  (keyword (.getStringProperty msg encoding-header-name)))


;; Encode

(defmulti encode (fn [_ _ {encoding :encoding}] (or encoding :clojure)))

(defmethod encode :clojure [session message options]
  "Stringify a clojure data structure"
  (set-encoding
   (.createTextMessage session (with-out-str
                                 (binding [*print-dup* true]
                                   (pr message))))
   :clojure))

(defmethod encode :json [session message options]
  "Stringify a json data structure"
  (set-encoding
   (.createTextMessage session (json/json-str message))
   :json))


;; Decode

(defmulti decode (fn [message] (get-encoding message)))

(defmethod decode :clojure [message]
  "Turn a string into a clojure data structure"
  (and message (load-string (.getText message))))

(defmethod decode :json [message]
  "Turn a string into a json data structure"
  (and message (json/read-json (.getText message))))

(defmethod decode :default [message]
  (throw (RuntimeException. (str "Received unknown message encoding: " (get-encoding message)))))


