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

(ns immutant.codecs.fressian
  "Provides support for using Fressian as an Immutant codec."
  (:require [immutant.codecs           :refer [make-codec register-codec]]
            [immutant.internal.util    :refer [kwargs-or-map->raw-map
                                               try-resolve
                                               try-resolve-throw]]
            [immutant.internal.options :refer [set-valid-options!
                                               validate-options]])
  (:import java.nio.ByteBuffer))

(defn ^:internal ^:no-doc fressian-codec
  [& options]
  (let [{:keys [name content-type read-handlers write-handlers]
         :or {name :fressian content-type "application/fressian"}}
        (-> options
          kwargs-or-map->raw-map
          (validate-options fressian-codec))
        fressian-write (try-resolve-throw `clojure.data.fressian/write
                         "add org.clojure/data.fressian to your dependencies.")
        fressian-read (try-resolve 'clojure.data.fressian/read)]
    (make-codec
      :name name
      :content-type content-type
      :type :bytes
      :encode (fn [data]
                (let [^ByteBuffer encoded (apply fressian-write data :footer? true
                                            (if write-handlers [:handlers write-handlers]))
                      bytes (byte-array (.remaining encoded))]
                  (.get encoded bytes)
                  bytes))
      :decode (fn [data]
                (try
                  (and data (apply fressian-read data
                              (if read-handlers [:handlers read-handlers])))
                  (catch Throwable e
                    (throw (ex-info (format "Invalid fressian-encoded data (type=%s)"
                                      (class data))
                             {:data data} e))))))))

(set-valid-options! fressian-codec #{:name :content-type :type
                                     :read-handlers :write-handlers})

(defn register-fressian-codec
  "Creates and registers a codec that can be used to encode to/decode from Fressian.

   To use vanilla Fressian, call this wih no options and use :fressian
   when passing an encoding to functions that take such.

   If you need to provide custom handlers, see the
   [Fressian wiki](https://github.com/clojure/data.fressian/wiki/Creating-custom-handlers)
   for more information on creating them. Note that custom handlers are
   *not* merged with the default handlers - you are responsible for
   that (as shown in the linked example).

   options can be a map or kwargs, with these valid keys [default]:

   * :name - the name of the encoding [:fressian]
   * :content-type - the content type for the encoding [\"application/fressian\"]
   * :write-handlers - the full set of handlers to use for writing. If not
     provided, the built-in Fressian defaults are used [nil]
   * :read-handlers the full set of handlers to use for reading. If not
     provided, the built-in Fressian defaults are used [nil]"
  [& options]
  (register-codec (apply fressian-codec options)))
