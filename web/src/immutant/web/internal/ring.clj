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

(ns ^{:no-doc true} immutant.web.internal.ring
    (:require [potemkin :refer [def-map-type]]
              [clojure.string :as str]
              [clojure.java.io :as io])
    (:import [java.io File InputStream OutputStream]
             [clojure.lang ISeq PersistentHashMap]))

(defprotocol SessionAttributes
  (attribute [session key])
  (set-attribute! [session key value]))

(def ring-session-key "ring-session-data")

(defn ring-session [session]
  (if session (attribute session ring-session-key)))
(defn set-ring-session! [session, data]
  (set-attribute! session ring-session-key data))

(defn session-expirer
  [timeout]
  (let [d (delay timeout)]
    (fn [session]
      (when-not (realized? d)
        (.setMaxInactiveInterval session @d))
      session)))

(def-map-type LazyMap [^java.util.Map m]
  (get [_ k default-value]
    (if (.containsKey m k)
      (let [v (.get m k)]
        (if (delay? v)
          @v
          v))
      default-value))
  (assoc [_ k v]
    (LazyMap.
      (assoc
          (if (instance? PersistentHashMap m)
            m
            (PersistentHashMap/create m)) k v)))
  (dissoc [_ k]
    (LazyMap.
      (dissoc
        (if (instance? PersistentHashMap m)
          m
          (PersistentHashMap/create m)) k)))
  (keys [_]
    (keys m)))

(defprotocol RingRequest
  (server-port [x])
  (server-name [x])
  (remote-addr [x])
  (uri [x])
  (query-string [x])
  (scheme [x])
  (request-method [x])
  (headers [x])
  (content-type [x])
  (content-length [x])
  (character-encoding [x])
  (ssl-client-cert [x])
  (body [x])
  (context [x])
  (path-info [x]))

(defn ring-request-map
  ([request & extra-entries]
     (->LazyMap
       (let [m (doto (java.util.HashMap. 24)
                 (.put :server-port        (delay (server-port request)))
                 (.put :server-name        (delay (server-name request)))
                 (.put :remote-addr        (delay (remote-addr request)))
                 (.put :uri                (delay (uri request)))
                 (.put :query-string       (delay (query-string request)))
                 (.put :scheme             (delay (scheme request)))
                 (.put :request-method     (delay (request-method request)))
                 (.put :headers            (delay (headers request)))
                 (.put :content-type       (delay (content-type request)))
                 (.put :content-length     (delay (content-length request)))
                 (.put :character-encoding (delay (character-encoding request)))
                 (.put :ssl-client-cert    (delay (ssl-client-cert request)))
                 (.put :body               (delay (body request)))
                 (.put :context            (delay (context request)))
                 (.put :path-info          (delay (path-info request))))]
         (doseq [[k v] extra-entries]
           (.put m k v))
         m))))

(defprotocol Headers
  (get-names [x])
  (get-values [x key])
  (set-header [x key value])
  (add-header [x key value]))

(defn headers->map [headers]
  (reduce
    (fn [accum ^String name]
      (assoc accum
        (-> name .toLowerCase)
        (->> name
          (get-values headers)
          (str/join ","))))
    {}
    (get-names headers)))

(defn write-headers
  [output, headers]
  (doseq [[^String k, v] headers]
    (if (coll? v)
      (doseq [value v]
        (add-header output k (str value)))
      (set-header output k (str v)))))


(defprotocol BodyWriter
  "Writing different body types to output streams"
  (write-body [body stream]))

(extend-protocol BodyWriter

  Object
  (write-body [body _]
    (throw (IllegalStateException. (str "Can't coerce body of type " (class body)))))

  nil
  (write-body [_ _]
    (throw (IllegalStateException. "Can't coerce nil body")))

  String
  (write-body [body ^OutputStream os]
    (.write os (.getBytes body)))

  ISeq
  (write-body [body ^OutputStream os]
    (doseq [fragment body]
      (write-body fragment os)))

  File
  (write-body [body ^OutputStream os]
    (io/copy body os))

  InputStream
  (write-body [body ^OutputStream os]
    (with-open [body body]
      (io/copy body os))))
