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
  "Create an HTTP server or return existing one matching :name"
  [& {:as opts}]
  (WunderBoss/findOrCreateComponent "web"
    (stringify-keys
      (merge {:name "default" :host "localhost" :port 8080} opts))))

(defn mount
  "Mount a Ring handler on a server"
  [server handler & {:as opts}]
  (.registerHandler server
    (undertow/create-http-handler handler)
    (stringify-keys (merge {:context-path "/"} opts))))

(defn unmount
  "Unmount handler at context path"
  ([] (unmount "/"))
  ([context-path] (unmount (server) context-path))
  ([server context-path]
     (.unregister server context-path)))

(defn mount-servlet
  "Mount a servlet on a server"
  [server servlet & {:as opts}]
  (.registerServlet server servlet
    (stringify-keys (merge {:context-path "/"} opts))))

(defmacro run
  "Composes server and mount fns; ensures handler is var-quoted"
  [handler & opts]
  (let [h (if (symbol? handler) `(var ~handler) handler)]
    `(mount (server ~@opts) ~h ~@opts)))
