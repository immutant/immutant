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

(ns immutant.web.core
  (:require [immutant.registry :as reg]))

(def ^{:dynamic true} current-servlet-request nil)

(def ^{:private true} servlet-filters (atom {}))

(defn get-servlet-filter [name]
  (@servlet-filters name))

(defn add-servlet-filter! [name filter-def filter-map handler]
  (swap! servlet-filters
         assoc
         name {:filter-def filter-def :filter-map filter-map :handler handler}))

(defn remove-servlet-filter! [name]
  (when-let [filter (@servlet-filters name)]
    (swap! servlet-filters dissoc name)
    filter))

(defn filter-name [path]
  (str "immutant.ring." (reg/fetch "app-name") "." path))

(defn normalize-subcontext-path 
  "normalize subcontext path so it matches Servlet Spec v2.3, section 11.2"
  [path]
  (loop [p path]
    (condp re-matches p
      #"^(/[^*]+)*/\*$" p                     ;; /foo/* || /*
      #"^(|[^/].*)"     (recur (str "/" p))   ;; prefix with /
      #".*?[^/]$"       (recur (str p "/"))   ;; add final /
      #"^(/[^*]+)*/$"   (recur (str p "*"))   ;; postfix with *
      (throw (IllegalArgumentException.
              (str "The context path \"" path "\" is invalid. It should be \"/\", \"/foo\", \"/foo/\", \"foo/\", or \"foo\""))))))
