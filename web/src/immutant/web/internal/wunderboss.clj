;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc ^:internal immutant.web.internal.wunderboss
    (:require [immutant.web.internal.undertow :refer [create-http-handler create-websocket-init-handler]]
              [immutant.web.internal.servlet  :refer [create-servlet websocket-servlet-filter-map]]
              [immutant.web.internal.ring     :refer [ring-request-map]]
              [immutant.internal.options  :refer [boolify extract-options opts->set
                                                  opts->defaults-map opts->map keywordize]]
              [immutant.internal.util     :as u]
              [immutant.util              :refer [in-container? context-path http-port]]
              [immutant.web.middleware    :refer [wrap-development]]
              [clojure.java.browse        :refer [browse-url]])
    (:import org.projectodd.wunderboss.WunderBoss
             io.undertow.server.HttpHandler
             [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]
             javax.servlet.Servlet
             [java.net ServerSocket InetSocketAddress]))

(def ^:internal register-defaults (-> (opts->defaults-map Web$RegisterOption)
                                    (boolify :dispatch)))

(def ^:internal create-defaults (opts->defaults-map Web$CreateOption))

(def ^:internal server-name
  (partial u/hash-based-component-name create-defaults))

(defn ^:internal available-port [opts]
  (if (= 0 (:port opts))
    (assoc opts :port
      (.getLocalPort (doto (ServerSocket.)
                       (.setReuseAddress true)
                       (.bind (InetSocketAddress. ^String (:host opts "") 0))
                       .close)))
    opts))

(defn ^:internal server [opts]
  (WunderBoss/findOrCreateComponent Web
    (server-name (select-keys opts (disj (opts->set Web$CreateOption) :auto-start)))
    (extract-options opts Web$CreateOption)))

(defn ^:internal mount [^Web server handler opts]
  (let [hdlr (if (fn? handler)
               (if (in-container?)
                 (create-servlet handler)
                 (create-websocket-init-handler
                   handler (create-http-handler handler)
                   ring-request-map))
               handler)
        opts (extract-options
               (if (in-container?)
                 (assoc opts :filter-map (websocket-servlet-filter-map))
                 opts)
               Web$RegisterOption)]
    (if (instance? Servlet hdlr)
      (try
        (.registerServlet server hdlr opts)
        (catch IllegalStateException e
          (if (re-find #"^UT010041" (.getMessage e))
            (throw (IllegalStateException.
                     "You can't call immutant.web/run outside of -main inside the container."
                     e))
            (throw e))))
      (.registerHandler server hdlr opts))))

(defn ^:internal mounts [opts]
  (-> (opts->map Web$RegisterOption)
    clojure.set/map-invert
    (select-keys [Web$RegisterOption/VHOSTS Web$RegisterOption/PATH])
    vals
    (->> (map keywordize)
      (select-keys opts))))

(defn ^:internal run-dmc* [run handler & options]
  (let [result (apply run (wrap-development handler) options)
        options (u/kwargs-or-map->map options)
        url (format "http://%s:%s%s%s"
              (:host options (:host create-defaults))
              (http-port result)
              (context-path)
              (:path options (:path register-defaults)))]
    (try
      (browse-url url)
      (catch Exception e
        (u/warn (format "Failed to browse to %s in run-dmc: %s" url (.getMessage e)))))
    result))
