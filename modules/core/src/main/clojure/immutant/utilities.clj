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

(ns immutant.utilities
  "Various utility functions."
  (:require [immutant.registry :as lookup]
            [clojure.string    :as str])
  (:import org.immutant.core.Closer))

(defn app-root
  "Returns a file pointing to the root dir of the application"
  []
  (lookup/fetch "app-root"))

(defn app-name
  "Returns the internal name for the app as Immutant sees it"
  []
  (lookup/fetch "app-name"))


(defn at-exit
  "Registers a function to be called when the application is undeployed.
Used internally to shutdown various services, but can be used by application code as well."
  [f]
  (if-let [^Closer closer (lookup/fetch "housekeeper")]
    (.atExit closer f)
    (println "WARN: Unable to register at-exit handler with housekeeper")))

;; ignoring reflection here, since it's only used at compile time
(defn ^{:private true} lookup-interface-address
  "Looks up the ip address from the proper service for the given name."
  [name]
  (-> (lookup/fetch (str "jboss.network." name))
      .getAddress
      .getHostAddress))

(def ^{:doc "Looks up the ip address for the AS management interface."}
  management-interface-address
  (partial lookup-interface-address "management"))

(def ^{:doc "Looks up the ip address for the AS public interface."}
  public-interface-address
  (partial lookup-interface-address "public"))

(def ^{:doc "Looks up the ip address for the AS unsecure interface."}
  unsecure-interface-address
  (partial lookup-interface-address "unsecure"))

(defn try-resolve
  "Tries to resolve the given namespace-qualified symbol"
  [sym]
  (try
    (require (symbol (namespace sym)))
    (resolve sym)
    (catch java.io.FileNotFoundException _)))

(defn try-resolve-any
  "Tries to resolve the given namespace-qualified symbols. Returns the
   first successfully resolved symbol, or nil if none of the given symbols
   resolve."
  [& syms]
  (if-let [sym (try-resolve (first syms))]
    sym
    (if-let [tail (seq (rest syms))]
      (apply try-resolve-any tail)
      (throw (IllegalArgumentException.
              "Unable to resolve a valid symbol from the given list.")))))

(defn map-to-seq
  "Takes a map and returns a sequence of (k1 v1 k2 v2...)"
  [m]
  (interleave (keys m) (vals m)))
