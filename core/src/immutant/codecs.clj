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
  "Common codecs used when [de]serializing data structures.

   The default registered codecs are:

   * :edn - encodes to/decodes from an EDN string
   * :json - encodes to/decodes from a JSON string. Requires `cheshire` as a
     dependency.
   * :none - performs no encoding

   You can enable :fressian encoding by calling
   {{immutant.codecs.fressian/register-fressian-codec}}, or make custom
   codecs with {{make-codec}}."
  (:require [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader     :as r]
            [immutant.internal.util   :refer [kwargs-or-map->map try-resolve
                                              try-resolve-throw]])
  (:import [org.projectodd.wunderboss.codecs BytesCodec Codec Codecs None StringCodec]
           java.nio.ByteBuffer))

(defmacro ^:internal ^:no-doc data-readers []
  (if (resolve 'clojure.core/*data-readers*)
    '(merge *data-readers* r/*data-readers*)
    'r/*data-readers*))

(defmacro make-codec
  "Creates a codec instance for the given settings.

   `settings` can be a map or kwargs, with these keys, most of which are
   required:

   * :name - The nickname for the codec. Can be a String or Keyword.
   * :content-type - The content type for the codec as a String.
   * :type - The type of data the codec encodes to/decodes from.
     Can be either :bytes or :string, and is optional, defaulting to
     :string.
   * :encode - A single-arity function that encodes its argument to
     the expected type.
   * :decode - A single-arity function that decodes its argument from
     the expected type to clojure data."
  [& settings]
  (let [{:keys [name content-type type encode decode] :or {type :string}}
        (kwargs-or-map->map settings)]
    `(proxy [~(if (= :bytes type)
                'org.projectodd.wunderboss.codecs.BytesCodec
                'org.projectodd.wunderboss.codecs.StringCodec)]
         [(clojure.core/name ~name) ~content-type]
       (encode [data#]
         (~encode data#))
       (decode [data#]
         (~decode data#)))))

(defonce ^:internal ^:no-doc ^Codecs codecs
  (-> (Codecs.)
    (.add None/INSTANCE)))

(defn register-codec
  "Registers a codec for use.

   `codec` should be the result of {{make-codec}}."
  [codec]
  (.add codecs codec))

(register-codec
  (make-codec
    :name :edn
    :content-type "application/edn"
    :encode pr-str
    :decode (fn [data]
                (try
                  (and data (edn/read-string {:readers (data-readers)} data))
                  (catch Throwable e
                    (throw (RuntimeException.
                             (str "Invalid edn-encoded data (type=" (class data) "): " data)
                             e)))))))

(register-codec
  (make-codec
    :name :json
    :content-type "application/json"
    :encode (fn [data]
              (let [cheshire-generate-string
                    (try-resolve-throw 'cheshire.core/generate-string
                      "add cheshire to your dependencies.")]
                (cheshire-generate-string data)))
    :decode (fn [data]
              (let [cheshire-parse-string (try-resolve-throw 'cheshire.core/parse-string
                                            "add cheshire to your dependencies.")]
                (try
                  (and data (cheshire-parse-string data true))
                  (catch Throwable e
                    (throw (RuntimeException.
                             (str "Invalid json-encoded data (type=" (class data) "): " data)
                             e))))))))

(defn codec-set
  "Returns a set of names for available codecs."
  []
  (into #{} (map #(-> % .name keyword) (.codecs codecs))))

(defn ^:internal ^:no-doc ^Codec lookup-codec
  [name-or-content-type]
  (if-let [codec (.forName codecs (name name-or-content-type))]
    codec
    (if-let [codec (.forContentType codecs (name name-or-content-type))]
      codec
      (throw (IllegalArgumentException.
               (format "Can't find codec for: %s. Available codecs: %s"
                 name-or-content-type (codec-set)))))))

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
