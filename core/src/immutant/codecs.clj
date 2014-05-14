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

(ns ^:no-doc immutant.codecs
  "Common codecs used when [de]serializing data structures."
  (:require [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader     :as r]
            [immutant.internal.util   :as u]))

(defmacro data-readers []
  (if (resolve 'clojure.core/*data-readers*)
    '(merge *data-readers* r/*data-readers*)
    'r/*data-readers*))

(def ^:private base-encoding->content-types
  {:clojure  "application/clojure"
   :edn      "application/edn"
   :fressian "application/fressian"
   :json     "application/json"
   :text     "text/plain"})

(def ^:private base-content-type->encodings
  (zipmap
    (vals base-encoding->content-types)
    (keys base-encoding->content-types)))

(defmulti encoding->content-type
  "Converts an encoding to a content-type."
  keyword)

(defmulti content-type->encoding
  "Converts a content-type to an encoding."
  identity)

(defmethod encoding->content-type :default [encoding]
  (if-let [content-type (base-encoding->content-types (keyword encoding))]
    content-type
    (throw (IllegalArgumentException.
             (str "Can't determine content-type for encoding: " encoding)))))

(defmethod content-type->encoding :default [content-type]
  (if-let [encoding (base-content-type->encodings content-type)]
    encoding
    (throw (IllegalArgumentException.
             (str "Can't determine encoding for content-type: " content-type)))))

(defmulti encode
  "Encode the data using the given content-type."
  (fn [_ & [content-type]] (or content-type :edn)))

(defmulti decode
  "Decode the data using the given content-type."
  (fn [_ & [content-type]] (or content-type :edn)))

(defmethod encode :clojure [data _]
  "Stringify a clojure data structure"
  (binding [*print-dup* true]
    (pr-str data)))

(defmethod decode :clojure [data _]
  "Turn a string into a clojure data structure"
  (try
    (and data
      (binding [r/*data-readers* (data-readers)]
        (r/read-string data)))
    (catch Throwable e
      (throw (RuntimeException.
               (str "Invalid clojure-encoded data (type=" (class data) "): " data)
               e)))))

(defmethod encode :edn [data & _]
  "Stringify an edn data structure"
  (pr-str data))

(defmethod decode :edn [data & _]
  "Turn an edn string into a clojure data structure"
  (try
    (and data (edn/read-string {:readers (data-readers)} data))
    (catch Throwable e
      (throw (RuntimeException.
               (str "Invalid edn-encoded data (type=" (class data) "): " data)
               e)))))

(defmethod encode :json [data _]
  "Stringify a json data structure"
  (if-let [generate-string (u/try-resolve 'cheshire.core/generate-string)]
    (generate-string data)
    (throw (Exception. "Can't encode json. Add cheshire to your dependencies."))))

(defmethod decode :json [data _]
  "Turn a json string into a clojure data structure"
  (if-let [parse-string (u/try-resolve 'cheshire.core/parse-string)]
    (try
      (and data (parse-string data true))
      (catch Throwable e
        (throw (RuntimeException.
                 (str "Invalid json-encoded data (type=" (class data) "): " data)
                 e))))
    (throw (Exception. "Can't decode json. Add cheshire to your dependencies."))))

(defmethod encode :fressian [data _]
  "Encode as fressian into a ByteBuffer."
  (if-let [write (u/try-resolve 'clojure.data.fressian/write)]
    (write data :footer? true)
    (throw (Exception. "Can't encode fressian. Add org.clojure/data.fressian to your dependencies."))))

(defmethod decode :fressian [data _]
  "Turn fressian bytes back in to clojure data"
  (if-let [read (u/try-resolve 'clojure.data.fressian/read)]
    (try
      (and data (read data))
      (catch Throwable e
        (throw (RuntimeException.
                 (str "Invalid fressian-encoded data (type=" (class data) "): " data)
                 e))))
    (throw (Exception. "Can't decode fressian. Add org.clojure/data.fressian to your dependencies."))))

(defmethod encode :none [data _]
  "Treat the payload as raw. No encoding is done."
  data)

(defmethod decode :none [data _]
  "Treats the payload as raw. No decoding is done."
  data)

(defmethod encode :default [_ encoding]
  (throw (RuntimeException. (str "Unknown message encoding: " encoding))))

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
