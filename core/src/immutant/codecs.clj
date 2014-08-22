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

(ns immutant.codecs
  "Common codecs used when [de]serializing data structures."
  (:require [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader     :as r]
            [immutant.internal.util   :as u])
  (:import [org.projectodd.wunderboss.codecs BytesCodec Codecs None StringCodec]))

(defmacro data-readers []
  (if (resolve 'clojure.core/*data-readers*)
    '(merge *data-readers* r/*data-readers*)
    'r/*data-readers*))

(defmacro make-codec
  "Creates a codec instance for the given settings.

   Takes the following settings, most of which are required:

   * :name - The nickname for the codec. Can be a String or Keyword.
   * :content-type - The content type for the codec as a String.
   * :type - The type of data the codec encodes to/decodes from.
     Can be either :bytes or :string, and is optional, defaulting to
     :string.
   * :encode - A single-arity function that encodes its argument to
     the expected type.
   * :encode - A single-arity function that decodes its argument from
     the expected type to clojure data."
  [{:keys [name content-type type encode decode] :or {type :string}}]
  `(proxy [~(if (= :bytes type) 'BytesCodec 'StringCodec)]
       [~(clojure.core/name name) ~content-type]
     (encode [data#]
       (~encode data#))
     (decode [data#]
       (~decode data#))))

(defonce ^:private codecs
  (-> (Codecs.)
    (.add None/INSTANCE)))

(defn register-codec!
  "Registers a codec for use.

   `codec` should be the result of {{make-codec}}."
  [codec]
  (.add codecs codec))

(register-codec!
  (make-codec
    {:name :edn
     :content-type "application/edn"
     :encode pr-str
     :decode (fn [data]
               (try
                 (and data (edn/read-string {:readers (data-readers)} data))
                 (catch Throwable e
                   (throw (RuntimeException.
                            (str "Invalid edn-encoded data (type=" (class data) "): " data)
                            e)))))}))

(register-codec!
  (make-codec
    {:name :fressian
     :content-type "application/fressian"
     :type :bytes
     :encode (fn [data]
               (if-let [write (u/try-resolve 'clojure.data.fressian/write)]
                 (let [data (write data :footer? true)
                       bytes (byte-array (.remaining data))]
                   (.get data bytes)
                   bytes)
                 (throw (IllegalArgumentException.
                          "Can't encode fressian. Add org.clojure/data.fressian to your dependencies."))))
     :decode (fn [data]
               (if-let [read (u/try-resolve 'clojure.data.fressian/read)]
                 (try
                   (and data (read data))
                   (catch Throwable e
                     (throw (RuntimeException.
                              (str "Invalid fressian-encoded data (type=" (class data) "): " data)
                              e))))
                 (throw (IllegalArgumentException.
                          "Can't decode fressian. Add org.clojure/data.fressian to your dependencies."))))}))

(register-codec!
  (make-codec
    {:name :json
     :content-type "application/json"
     :encode (fn [data]
               (if-let [generate-string (u/try-resolve 'cheshire.core/generate-string)]
                 (generate-string data)
                 (throw (IllegalArgumentException. "Can't encode json. Add cheshire to your dependencies."))))
     :decode (fn [data]
               (if-let [parse-string (u/try-resolve 'cheshire.core/parse-string)]
                 (try
                   (and data (parse-string data true))
                   (catch Throwable e
                     (throw (RuntimeException.
                              (str "Invalid json-encoded data (type=" (class data) "): " data)
                              e))))
                 (throw (IllegalArgumentException. "Can't decode json. Add cheshire to your dependencies."))))}))

(defn codec-set
  "Returns a set of names for available codecs."
  []
  (into #{} (map #(-> % .name keyword) (.codecs codecs))))

(defn lookup-codec
  [name-or-content-type]
  (if-let [codec (.forName codecs (name name-or-content-type))]
    codec
    (if-let [codec (.forContentType codecs (name name-or-content-type))]
      codec
      (throw (IllegalArgumentException.
               (str "Can't find codec for: " name-or-content-type))))))

(defn encode
  "Encodes `data` using the codec for `encoding`.

   `encoding` can be the name of the encoding or its
   content-type. `encoding` defaults to :edn."
  ([data]
     (encode data :edn))
  ([data encoding]
     (.encode (lookup-codec encoding) data)))

(defn decode
  "Decodes `data` using the codec for `encoding`.

   `encoding` can be the name of the encoding or its
   content-type. `encoding` defaults to :edn."
  ([data]
     (decode data :edn))
  ([data encoding]
     (.decode (lookup-codec encoding) data)))
