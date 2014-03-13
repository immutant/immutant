(ns immutant.web.undertow
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [io.undertow.server HttpHandler HttpServerExchange]
           [io.undertow.util HeaderMap Headers HttpString]
           [java.io File InputStream OutputStream]
           java.nio.channels.FileChannel
           clojure.lang.ISeq))

(defn- headers->map [^HeaderMap headers]
  (reduce
    (fn [accum header-name]
      (assoc accum
        (-> header-name .toString .toLowerCase)
        (->> header-name
          (.get headers)
          (str/join ","))))
    {}
    (.getHeaderNames headers)))

(defn- ring-request [^HttpServerExchange exchange]
  (let [headers (.getRequestHeaders exchange)
        content-type (.getFirst headers Headers/CONTENT_TYPE)]
    ;; TODO: context, path-info ?
    {:server-port (-> exchange .getDestinationAddress .getPort)
     :server-name (.getHostName exchange)
     :remote-addr (-> exchange .getSourceAddress .getAddress .getHostAddress)
     :uri (.getRequestURI exchange)
     :query-string (.getQueryString exchange)
     :scheme (-> exchange .getRequestScheme keyword)
     :request-method (-> exchange .getRequestMethod .toString .toLowerCase keyword)
     :content-type content-type
     :content-length (.getRequestContentLength exchange)
     :character-encoding (if content-type
                           (Headers/extractTokenFromHeader content-type "charset"))
     ;; TODO: :ssl-client-cert
     :headers (headers->map headers)
     :body (.getInputStream exchange)}))

(defn- merge-headers [^HeaderMap to-headers from-headers]
  (doseq [[k v] from-headers]
    (let [k (HttpString. k)]
      (if (coll? v)
        (.addAll to-headers k v)
        (.add to-headers k v)))))

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
  (with-open [output-stream (.getOutputStream exchange)]
    (if-let [response (f (ring-request exchange))]
      (write-response exchange response)
      (throw (NullPointerException. "Ring handler returned nil")))))

(defn create-http-handler [handler]
  (reify HttpHandler
    (^void handleRequest [this ^HttpServerExchange exchange]
      (if (.isInIoThread exchange)
        (.dispatch exchange this)
        (handle-request handler exchange)))))

