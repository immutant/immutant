;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

(ns immutant.registry
  (:import (org.jboss.msc.service ServiceName)))

(def ^{:private true} registry (java.util.HashMap.))
(def ^{:private true} msc-registry nil)

(defn set-msc-registry [v]
  (def msc-registry v))

(defn ^{:private true} get-from-msc [name]
  (if msc-registry
    (let [key (if (string? name) (ServiceName/parse name) name)
          value (.getService msc-registry key)]
      (and value (.getValue value)))))
  
(defn put [k v]
  (.put registry k v))

(defn fetch [name]
  (or (get registry name) (get-from-msc name)))

