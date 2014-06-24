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
              [immutant.internal.util   :as u])
    (:import [org.projectodd.wunderboss.codecs BytesCodec Codecs None StringCodec]))

(defmacro data-readers []
  (if (resolve 'clojure.core/*data-readers*)
    '(merge *data-readers* r/*data-readers*)
    'r/*data-readers*))

(def edn-codec
  (proxy [StringCodec] ["edn" "application/edn"]
    (decode [data]
      (try
        (and data (edn/read-string {:readers (data-readers)} data))
        (catch Throwable e
          (throw (RuntimeException.
                   (str "Invalid edn-encoded data (type=" (class data) "): " data)
                   e)))))

    (encode [data]
      (pr-str data))))

(def json-codec
  (proxy [StringCodec] ["json" "application/json"]
    (decode [data]
      (if-let [parse-string (u/try-resolve 'cheshire.core/parse-string)]
        (try
          (and data (parse-string data true))
          (catch Throwable e
            (throw (RuntimeException.
                     (str "Invalid json-encoded data (type=" (class data) "): " data)
                     e))))
        (throw (IllegalArgumentException. "Can't decode json. Add cheshire to your dependencies."))))

    (encode [data]
      (if-let [generate-string (u/try-resolve 'cheshire.core/generate-string)]
        (generate-string data)
        (throw (IllegalArgumentException. "Can't encode json. Add cheshire to your dependencies."))))))

(def fressian-codec
  (proxy [BytesCodec] ["fressian" "application/fressian"]
    (decode [data]
      (if-let [read (u/try-resolve 'clojure.data.fressian/read)]
        (try
          (and data (read data))
          (catch Throwable e
            (throw (RuntimeException.
                     (str "Invalid fressian-encoded data (type=" (class data) "): " data)
                     e))))
        (throw (IllegalArgumentException.
                 "Can't decode fressian. Add org.clojure/data.fressian to your dependencies."))))

    (encode [data]
      (if-let [write (u/try-resolve 'clojure.data.fressian/write)]
        (let [data (write data :footer? true)
              bytes (byte-array (.remaining data))]
          (.get data bytes)
          bytes)
        (throw (IllegalArgumentException.
                 "Can't encode fressian. Add org.clojure/data.fressian to your dependencies."))))))

(def codecs
  (-> (Codecs.)
    (.add None/INSTANCE)
    (.add edn-codec)
    (.add fressian-codec)
    (.add json-codec)))

(defn add-codec! [codec]
  (.add codecs codec))

(defn lookup-codec [encoding]
  (if-let [codec (.forName codecs (name encoding))]
    codec
    (if-let [codec (.forContentType codecs (name encoding))]
      codec
      (throw (IllegalArgumentException.
               (str "Can't find codec for: " encoding))))))

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
   content-type. `encoding` defaults to :none."
  ([data]
     (decode data :none))
  ([data encoding]
     (.decode (lookup-codec encoding) data)))
