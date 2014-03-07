;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.web
  "Associate one or more Ring handlers with your application, mounted
   at unique context paths"
  (:require [immutant.logging        :as log]
            [immutant.util           :as util]
            [immutant.web.undertow   :as undertow]
            [clojure.walk            :refer [stringify-keys]]
            [immutant.web.middleware :refer [add-middleware]])
  (:import org.projectodd.wunderboss.WunderBoss))

(defn server
  "Create an HTTP server"
  [& {:as opts}]
  (WunderBoss/findOrCreateComponent "web"
    (stringify-keys
      (merge {:name "default" :host "localhost" :port 8080} opts))))

(defmacro mount-handler
  "Mount a Ring handler on a server"
  [server handler & {:as opts}]
  (let [h (if (symbol? handler) `(var ~handler) handler)]
    `(.registerHandler ~server
       (undertow/create-http-handler ~h)
       (stringify-keys (merge {:context-path "/"} ~opts)))))

(defmacro run
  "Creates a server, mounts the handler at root context and runs it"
  [handler & opts]
  `(mount-handler (server ~@opts) ~handler ~@opts))

(defn unmount
  "Unmount the handler at a given context path"
  ([] (unmount "/"))
  ([context-path] (unmount (server) context-path))
  ([server context-path]
     (.unregister server context-path)))

(defn mount-servlet
  "Mount a servlet on a server"
  [server servlet & {:as opts}]
  (.registerServlet server servlet
    (stringify-keys (merge {:context-path "/"} opts))))
