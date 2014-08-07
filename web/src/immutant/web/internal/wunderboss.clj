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

(ns ^:no-doc ^:internal immutant.web.internal.wunderboss
    (:require [immutant.web.internal.undertow :refer [create-http-handler]]
              [immutant.web.internal.servlet  :refer [create-servlet]]
              [immutant.internal.options  :refer [extract-options opts->set opts->defaults-map opts->map keywordize]]
              [immutant.internal.util     :as u]
              [immutant.util              :refer [in-container?]]
              [immutant.web.middleware    :refer [wrap-dev-middleware]]
              [clojure.java.browse        :refer [browse-url]])
    (:import org.projectodd.wunderboss.WunderBoss
             io.undertow.server.HttpHandler
             [org.projectodd.wunderboss.web Web Web$CreateOption Web$RegisterOption]
             javax.servlet.Servlet
             [io.undertow.server.session InMemorySessionManager SessionCookieConfig SessionAttachmentHandler]))

(def ^:internal register-defaults (opts->defaults-map Web$RegisterOption))
(def ^:internal create-defaults (opts->defaults-map Web$CreateOption))

(def ^:internal session-manager
  (InMemorySessionManager. "thismayneedabettername", -1))

(def ^:internal server-name
  (partial u/hash-based-component-name create-defaults))

(defn ^:internal server [opts]
  (WunderBoss/findOrCreateComponent Web
    (server-name (select-keys opts (opts->set Web$CreateOption)))
    (extract-options opts Web$CreateOption)))

(defn ^:internal mount [server handler opts]
  (let [opts (extract-options opts Web$RegisterOption)
        hdlr (if (fn? handler)
               (if (in-container?)
                 (create-servlet handler)
                 (create-http-handler handler))
               handler)]
    (if (instance? Servlet handler)
      (.registerServlet server hdlr opts)
      (.registerHandler server
        (SessionAttachmentHandler.
          hdlr
          session-manager
          (SessionCookieConfig.))
        opts))))

(defn ^:internal mounts [opts]
  (-> (opts->map Web$RegisterOption)
    clojure.set/map-invert
    (select-keys [Web$RegisterOption/VHOSTS Web$RegisterOption/PATH])
    vals
    (->> (map keywordize)
      (select-keys opts))))

(defn ^:internal run-dmc* [run handler & options]
  (let [result (apply run (wrap-dev-middleware handler) options)
        options (u/kwargs-or-map->map options)]
    (browse-url (format "http://%s:%s%s"
                  (:host options (:host create-defaults))
                  (:port options (:port create-defaults))
                  (:path options (:path register-defaults))))
    result))
