;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
  (:require [immutant.registry     :as reg]
            [clojure.tools.logging :as log])
  (:import (org.apache.catalina.deploy  FilterDef FilterMap)
           (org.immutant.web.servlet    RingFilter))
  (:use immutant.web.core))

(defn start
  "Registers a Ring handler that will be called when requests are received on the given sub-context-path.
If no sub-context-path is given, \"/\" is assumed."
  ([handler]
     (start "/" handler))
  ([sub-context-path handler]
      (log/info "Registering ring handler at sub-context path:" sub-context-path)
      (let [name (filter-name sub-context-path)
            sub-context-path (normalize-subcontext-path sub-context-path)
            filter-def (doto (FilterDef.)
                         (.setFilterName name)
                         (.setFilterClass (.getName RingFilter))
                         (.addInitParameter "sub-context" sub-context-path))
            filter-map (doto (FilterMap.)
                         (.addURLPattern sub-context-path)
                         (.setFilterName name))]
        (add-servlet-filter! name filter-def filter-map handler)
        (doto (reg/fetch "web-context")
          (.addFilterDef filter-def)
          (.addFilterMap filter-map)))))

(defn stop
  "Deregisters the Ring handler attached to the given sub-context-path. If no sub-context-path is given,
\"/\" is assumed."
  ([]
     (stop "/"))
  ([sub-context-path]
     (if-let [{:keys [filter-def filter-map]} (remove-servlet-filter! (filter-name sub-context-path))]
       (do
         (log/info "Deregistering ring handler at sub-context path:" sub-context-path)
         (doto (reg/fetch "web-context")
           (.removeFilterMap filter-map)
           (.removeFilterDef filter-def)))
       (log/warn "Attempted to deregister ring handler at sub-context path:" sub-context-path ", but none found"))))

(defmacro src-dir
  "Find the absolute path to a parent directory, 'src' by default.
   Useful for ring.middleware.reload-modified/wrap-reload-modified"
  [& [dir]]
  (let [target (or dir "src")]
    `(loop [f# (.getParentFile (java.io.File. *file*))]
       (if f#
         (let [d# (java.io.File. f# ~target)]
           (if (and (.exists d#) (.isDirectory d#))
             (str d#)
             (recur (.getParentFile f#)))))))) 
