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

(ns ^{:no-doc true} immutant.web.undertow
    (:require [clojure.string :as str]
              [clojure.java.io :as io]
              [immutant.web.util :refer [->LazyMap]])
    (:import [io.undertow.server HttpHandler HttpServerExchange]
             [io.undertow.util HeaderMap Headers HttpString]
             [java.io File InputStream OutputStream]
             java.nio.channels.FileChannel
             clojure.lang.ISeq))

(defn- headers->map [^HeaderMap headers]
  (reduce
    (fn [accum ^HttpString header-name]
      (assoc accum
        (-> header-name .toString .toLowerCase)
        (if (> 1 (.count headers header-name))
          (->> header-name
            (.get headers)
            (str/join ","))
          (.getFirst headers header-name))))
    {}
    (.getHeaderNames headers)))

(defn- ring-request [^HttpServerExchange exchange]
  (let [headers (.getRequestHeaders exchange)
        content-type (delay (.getFirst ^HeaderMap headers Headers/CONTENT_TYPE))]
    ;; TODO: context, path-info ?
    (->LazyMap {:server-port (delay (-> exchange .getDestinationAddress .getPort))
                :server-name (delay (.getHostName exchange))
                :remote-addr (delay (-> exchange .getSourceAddress .getAddress .getHostAddress))
                :uri (delay (.getRequestURI exchange))
                :query-string (delay (.getQueryString exchange))
                :scheme (delay (-> exchange .getRequestScheme keyword))
                :request-method (delay (-> exchange .getRequestMethod .toString .toLowerCase keyword))
                :content-type content-type
                :content-length (delay (.getRequestContentLength exchange))
                :character-encoding (delay (if @content-type
                                             (Headers/extractTokenFromHeader @content-type "charset")))
                ;; TODO: :ssl-client-cert
                :headers (delay (headers->map headers))
                :body (delay (.getInputStream exchange))})))

(defn- merge-headers [^HeaderMap to-headers from-headers]
  (doseq [[^String k v] from-headers]
    (let [^HttpString k (HttpString. k)]
      (if (coll? v)
        (.addAll to-headers k v)
        (.add to-headers k ^String v)))))

(defprotocol BodyWriter
  (write-body [body exchange]))

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

(defn- write-response [^HttpServerExchange exchange {:keys [status headers body]}]
  (when status
    (.setResponseCode exchange status))
  (merge-headers (.getResponseHeaders exchange) headers)
  (write-body body (.getOutputStream exchange)))

(defn handle-request [f ^HttpServerExchange exchange]
  (.startBlocking exchange)
  (try
    (if-let [response (f (ring-request exchange))]
      (write-response exchange response)
      (throw (NullPointerException. "Ring handler returned nil")))
    (finally
      (.endExchange exchange))))

(defn create-http-handler [handler]
  (reify HttpHandler
    (^void handleRequest [this ^HttpServerExchange exchange]
      (if (.isInIoThread exchange)
        (.dispatch exchange this)
        (handle-request handler exchange)))))

