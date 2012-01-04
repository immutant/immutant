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
  (:require [immutant.registry :as reg])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.catalina.deploy FilterDef FilterMap))
  (:import (org.immutant.web.servlet RingFilter))
  (:use immutant.web.core))

(defn start
  "Creates a web endpoint at subcontext-path beneath the context-path of the app, handled by handler"
  [subcontext-path handler]
  (log/info "Starting web service at sub-context path:" subcontext-path)
  (let [name (filter-name subcontext-path)
        filter-def (doto (FilterDef.)
                     (.setFilterName name)
                     (.setFilterClass (.getName RingFilter))
                     (.addInitParameter RingFilter/CLOJURE_FUNCTION
                                        (with-out-str
                                          (binding [*print-dup* true]
                                            (pr handler)))))

        filter-map (doto (FilterMap.)
                     (.addURLPattern (normalize-subcontext-path subcontext-path))
                     (.setFilterName name))]

    (doto (reg/fetch "web-context")
      (.addFilterDef filter-def)
      (.addFilterMap filter-map))

     (dosync (alter filters
                      assoc
                      name {:filter filter-def :map filter-map}))))

(defn stop 
  "Tears down the web endpoint at subcontext-path beneath the context-path of the app"
  [subcontext-path]
  (if-let [{filter-def :filter filter-map :map} (@filters (filter-name subcontext-path))]
    (do
      (log/info "Stopping web service at sub-context path:" subcontext-path)
      (doto (reg/fetch "web-context")
        (.removeFilterMap filter-map)
        (.removeFilterDef filter-def)))
    (log/warn "Attempted to stop web service at sub-context path:" subcontext-path ", but none found")))



