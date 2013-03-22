;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns immutant.util
  "Various utility functions."
  (:require [immutant.registry :as registry]
            [clojure.string    :as str]
            [clojure.java.io   :as io]))

(defn in-immutant?
  "Returns true if running inside an Immutant container"
  []
  (not (nil? (registry/get "housekeeper"))))

(defmacro if-in-immutant
  "Executes the 'yes' branch if inside immutant, the 'no' branch otherwise."
  [yes no]
  `(if (in-immutant?)
     ~yes
     ~no))

(defn app-root
  "Returns a file pointing to the root dir of the application"
  []
  (registry/get "app-root"))

(defn app-name
  "Returns the internal name for the app as Immutant sees it"
  []
  (registry/get "app-name"))

(defn app-relative
  "Returns a file relative to app-root"
  [& path]
  (if-let [root (app-root)]
    (apply io/file root path)))

(defn at-exit
  "Registers a function to be called when the application is undeployed.
   Used internally to shutdown various services, but can be used by
   application code as well."
  [f]
  (if-let [closer (registry/get "housekeeper")]
    (.atExit closer f)
    (println "WARN: Unable to register at-exit handler with housekeeper")))

;; ignoring reflection here, since it's only used at compile time
(defn ^{:private true} lookup-interface-address
  "Looks up the ip address from the proper service for the given name."
  [name]
  (-> (registry/get (str "jboss.network." name))
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

(defn http-port
  "Returns the HTTP port for the embedded web server"
  []
  (if-let [server (registry/get "jboss.web.connector.http")]
    (.getPort server)))

(defn context-path
  "Returns the HTTP context path for the deployed app"
  []
  (if-let [context (immutant.registry/get "web-context")]
    (.getName context)))

(defn app-uri
  "Returns the base URI for the app, given a host [localhost]"
  [& [host]]
  (let [host (or host "localhost")]
    (str "http://" host ":" (http-port) (context-path))))

(defmacro with-tccl [& body]
  ;; not everything uses baseLoader like it should, and expects
  ;; the TCCL to be set instead, so we do so
  ;; I'm glaring at you, clojurescript
  `(let [thread# (Thread/currentThread)
         original# (.getContextClassLoader thread#)]
     (.setContextClassLoader thread# (clojure.lang.RT/baseLoader))
     (try
       ~@body
       (finally
         (.setContextClassLoader thread# original#)))))

(defn try-resolve
  "Tries to resolve the given namespace-qualified symbol"
  [sym]
  (try
    (require (symbol (namespace sym)))
    (resolve sym)
    (catch java.io.FileNotFoundException _)
    (catch RuntimeException _)))

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

(defn mapply [f & args]
  "Applies args to f, and expands the last arg into a kwarg seq if it is a map"
  (apply f (apply concat (butlast args) (last args))))

(defmacro backoff
  "A simple backoff strategy that retries body in the event of error.
   The first retry occurs after sleeping start milliseconds, the next
   after start*2 ms, and so on, until the sleep time exceeds end ms,
   at which point the caught error is tossed."
  [start end & body]
  `(loop [x# ~start]
     (let [result# (try
                    ~@body
                    (catch Exception e# (if (> x# ~end) (throw e#))))]
       (or result# (do (Thread/sleep x#) (recur (* 2 x#)))))))

