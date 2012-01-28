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
  (:require [immutant.registry :as reg]
            [clojure.tools.logging :as log])
  (:import (org.apache.catalina.deploy FilterDef FilterMap)
           (org.immutant.web.servlet RingFilter))
  (:use immutant.web.core))

(defn start
  "Creates a web endpoint at subcontext-path beneath the context-path of the app, handled by handler"
  [subcontext-path handler]
  (log/info "Starting web service at sub-context path:" subcontext-path)
  (let [name (filter-name subcontext-path)
        filter-def (doto (FilterDef.)
                     (.setFilterName name)
                     (.setFilterClass (.getName RingFilter)))
        filter-map (doto (FilterMap.)
                     (.addURLPattern (normalize-subcontext-path subcontext-path))
                     (.setFilterName name))]
    (add-servlet-filter! name filter-def filter-map handler)
    (doto (reg/fetch "web-context")
      (.addFilterDef filter-def)
      (.addFilterMap filter-map))))

(defn stop 
  "Tears down the web endpoint at subcontext-path beneath the context-path of the app"
  [subcontext-path]
  (if-let [{:keys [filter-def filter-map]} (remove-servlet-filter! (filter-name subcontext-path))]
    (do
      (log/info "Stopping web service at sub-context path:" subcontext-path)
      (doto (reg/fetch "web-context")
        (.removeFilterMap filter-map)
        (.removeFilterDef filter-def)))
    (log/warn "Attempted to stop web service at sub-context path:" subcontext-path ", but none found")))

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
