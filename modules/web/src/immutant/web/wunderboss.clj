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

(ns ^{:no-doc true} immutant.web.wunderboss
    (:require [immutant.logging :as log] )
    (:import [org.projectodd.wunderboss WunderBoss]))

(defonce ^:private started-containers (atom {}))

(defn stop
  [context]
  (when-let [container (get @started-containers context)]
    (.stop container)
    (swap! started-containers dissoc context)
    true))

(defn start
  [context handler {:as opts}]
  (when (get @started-containers context)
    (log/warn (str "Replacing handler at context " context))
    (stop context))
  (let [opts (into {} (map (fn [[k v]] [(name k) v]) opts))
        container (.configure (WunderBoss.) "web" opts)
        app (.newApplication container "clojure" {"ring-handler" handler})]
    (swap! started-containers assoc context container)
    (.start app "ring" {"context" context})))
