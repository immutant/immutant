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

(ns immutant.web.middleware
  "Ring middleware useful with immutant.web"
  (:require [immutant.internal.util         :refer [try-resolve]]
            [immutant.util                  :refer [in-container?]]
            [immutant.web.async             :refer [as-channel]]
            [immutant.web.internal.servlet  :refer [wrap-servlet-session]]
            [immutant.web.internal.undertow :refer [wrap-undertow-session]]))

(defn wrap-development
  "Wraps stacktrace and reload middleware with the correct :dirs
  option set, but will toss an exception if either the passed handler
  isn't a normal Clojure function or ring/ring-devel isn't available
  on the classpath"
  [handler]
  (if-not (or (fn? handler) (and (var? handler) (fn? (deref handler))))
    (throw (RuntimeException. "Middleware only applies to regular Clojure functions")))
  (let [wrap-reload           (try-resolve 'ring.middleware.reload/wrap-reload)
        wrap-stacktrace       (try-resolve 'ring.middleware.stacktrace/wrap-stacktrace)
        classpath-directories (try-resolve 'clojure.java.classpath/classpath-directories)]
    (if wrap-reload
      (-> handler
        (wrap-reload {:dirs (map str (classpath-directories))})
        wrap-stacktrace)
      (throw (RuntimeException. "Middleware requires ring/ring-devel; check your dependencies")))))

(defn wrap-session
  "Uses the session from either Undertow or, when deployed to an app
  server cluster such as WildFly or EAP, the servlet's
  possibly-replicated HttpSession. By default, sessions will timeout
  after 30 minutes of inactivity.

  Supported options:

     * :timeout The number of seconds of inactivity before session expires [1800]
     * :cookie-name The name of the cookie that holds the session key [\"JSESSIONID\"]
     * :cookie-attrs A map of attributes to associate with the session cookie [nil]

  A :timeout value less than or equal to zero indicates the session
  should never expire.

  The following :cookie-attrs keys are supported:

     * :path      - the subpath the cookie is valid for
     * :domain    - the domain the cookie is valid for
     * :max-age   - the maximum age in seconds of the cookie
     * :secure    - set to true if the cookie requires HTTPS, prevent HTTP access
     * :http-only - set to true if the cookie is valid for HTTP and HTTPS only
                    (ie. prevent JavaScript access)"
  ([handler]
     (wrap-session handler {}))
  ([handler options]
     (let [options (merge {:timeout (* 30 60)} options)]
       (if (in-container?)
         (wrap-servlet-session handler options)
         (wrap-undertow-session handler options)))))

(defn wrap-websocket
  "Middleware to attach websocket callbacks to a Ring handler.

  The following callbacks are supported, where `channel` is an object
  extended to [[immutant.web.async/Channel]], `handshake` is extended
  to [[immutant.web.async/WebsocketHandshake]], `throwable` is a Java
  exception, and `message` may be either a `String` or a `byte[]`:

  * :on-message `(fn [channel message])`
  * :on-open    `(fn [channel])`
  * :on-close   `(fn [channel {:keys [code reason]}])`
  * :on-error   `(fn [channel throwable])`

  If handler is nil, a 404 status will be returned for any
  non-websocket request."
  ([handler key value & key-values]
   (wrap-websocket handler (apply hash-map key value key-values)))
  ([handler callbacks]
   (fn [request]
     (if (:websocket? request)
       (as-channel request callbacks)
       (merge {:status 404} (when handler (handler request)))))))
