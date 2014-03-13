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
            [immutant.web.undertow   :as undertow]
            [immutant.util           :refer [concat-valid-options extract-options
                                             validate-options enum->set mapply]]
            [immutant.web.middleware :refer [add-middleware]])
  (:import org.projectodd.wunderboss.WunderBoss
           org.projectodd.wunderboss.web.Web$CreateOption
           org.projectodd.wunderboss.web.Web$RegisterOption))

(defn ^{:valid-options (conj (enum->set Web$CreateOption) :name)}
  server
  "Create an HTTP server or return existing one matching :name"
  [& {:as opts}]
  (let [opts (->> opts
               (merge {:name "default" :host "localhost" :port 8080})
               (validate-options server))]
    (WunderBoss/findOrCreateComponent "web"
      (:name opts)
      (extract-options opts Web$CreateOption))))

(defn ^{:valid-options (enum->set Web$RegisterOption)}
  mount
  "Mount a Ring handler on a server"
  [server handler & {:as opts}]
  (let [opts (->> opts
               (merge {:context-path "/"})
               (validate-options mount))]
    (.registerHandler server
      (undertow/create-http-handler handler)
      (extract-options opts Web$RegisterOption))))

(defn unmount
  "Unmount handler at context path"
  ([] (unmount "/"))
  ([context-path] (unmount (server) context-path))
  ([server context-path]
     (.unregister server context-path)))

(defn mount-servlet
  "Mount a servlet on a server"
  [server servlet & {:as opts}]
  (let [opts (->> opts
               (merge {:context-path "/"})
               (validate-options mount))]
    (.registerServlet server servlet
      (extract-options opts Web$RegisterOption))))

(defmacro ^{:valid-options (concat-valid-options #'server #'mount)}
  run
  "Composes server and mount fns; ensures handler is var-quoted"
  [handler & {:as opts}]
  (let [handler (if (and (symbol? handler) (resolve handler))
                  `(var ~handler)
                  handler)]
    `(let [options# (validate-options run ~opts)]
       (mapply mount (mapply server options#) ~handler options#))))
