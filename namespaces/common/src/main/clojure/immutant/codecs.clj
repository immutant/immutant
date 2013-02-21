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

(ns immutant.codecs
  "Common codecs used when [de]serializing data structures."
  (:require [cheshire.core :as json]))

;; Encode

(defmulti encode
  "Encode the data using the given encoding."
  (fn [_ & [encoding]] (or encoding :edn)))

(defmethod encode :clojure [data _]
  "Stringify a clojure data structure"
  (binding [*print-dup* true]
    (str "_|clojure|_" (pr-str data))))

(defmethod encode :edn [data & _]
  "Stringify an edn data structure"
  (str "_|edn|_" (pr-str data)))

(defmethod encode :json [data _]
  "Stringify a json data structure"
  (str "_|json|_" (json/generate-string data)))

(defmethod encode :none [data _]
  "Treat the payload as raw. No encoding is done."
   data)

(defmethod encode :default [_ encoding]
  (throw (RuntimeException. (str "Unknown message encoding: " encoding))))


;; Decode

(defmulti decode
  "Decode the data using the given encoding."
  (fn [data & [encoding]]
    (if-let [m (and (string? data) (re-find #"^_\|(.*?)\|_" data))]
      (keyword (str (second m) "-with-header"))
      (or encoding :edn))))

(defmethod decode :clojure [data & _]
  "Turn a string into a clojure data structure"
  (and data (read-string data)))

(defmethod decode :clojure-with-header [data & _]
  "Turn a string into a clojure data structure"
  (and data (read-string (.substring data 11))))

(defmethod decode :edn [data & _]
  "Turn a string into an edn data structure"
  (and data (read-string data)))

(defmethod decode :edn-with-header [data & _]
  "Turn a string into an edn data structure"
  (and data (read-string (.substring data 7))))

(defmethod decode :json [data & _]
  "Turn a string into a json data structure"
  (and data (json/parse-string data true)))

(defmethod decode :json-with-header [data & _]
  "Turn a string into a json data structure"
  (and data (json/parse-string (.substring data 8) true)))

(defmethod decode :none [data & _]
  "Treats the payload as raw. No decoding is done."
  data)

(defmethod decode :default [_ encoding]
  (throw (RuntimeException. (str "Unknown message encoding: " encoding))))


;; Fallback to Java serialization for undefined print-dup methods

(defn serialize [object]
  (with-open [baos (java.io.ByteArrayOutputStream.)
              oos (java.io.ObjectOutputStream. baos)]
    (.writeObject oos object)
    (.toString baos "ISO-8859-1")))

(defn deserialize [^String s]
  (with-open [bais (java.io.ByteArrayInputStream. (.getBytes s "ISO-8859-1"))
              ois (java.io.ObjectInputStream. bais)]
    (.readObject ois)))

(defmethod clojure.core/print-dup :default [o ^java.io.Writer w]
  (.write w "#=(immutant.codecs/deserialize ")
  (print-dup (serialize o) w)
  (.write w ")"))
