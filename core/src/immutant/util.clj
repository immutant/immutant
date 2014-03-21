;; Copyright 2014 Red Hat, Inc, and individual contributors.
;; 
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; 
;; http://www.apache.org/licenses/LICENSE-2.0
;; 
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.util
  "Various utility functions."
  (:require [immutant.registry      :as registry]
            [immutant.logging       :as log]
            [clojure.string         :as str]
            [clojure.java.io        :as io]
            [clojure.java.classpath :as cp]
            [clojure.walk           :refer [stringify-keys]])
  (:import clojure.lang.IDeref
           java.util.EnumSet
           org.projectodd.wunderboss.WunderBoss))


;; TODO: cleanup this namespace

(defn in-immutant?
  "Returns true if running inside an Immutant container"
  []
  (not (nil? (registry/get "housekeeper"))))

(defn app-root
  "Returns a file pointing to the root dir of the application"
  []
  (io/file (get (WunderBoss/options) "root")))

(defn app-name
  "Returns the internal name for the app as Immutant sees it"
  []
  (registry/get "app-name"))

(defn app-relative
  "Returns an absolute file relative to app-root"
  [& path]
  (if-let [root (app-root)]
    (apply io/file root path)
    (apply io/file path)))

(defn classpath
  "Returns the effective classpath for the app"
  []
  (cp/classpath))

(defn at-exit
  "Registers a function to be called when the application is undeployed.
   Used internally to shutdown various services, but can be used by
   application code as well."
  [f]
  (if-let [closer (registry/get "housekeeper")]
    (.atExit closer f)))

(defn ^:internal lookup-interface-address
  "Looks up the ip address from the proper service for the given name."
  [iface]
  (if iface
    (try
      (if-let [addr (registry/get (str "jboss.network." (name iface)))]
        (-> addr
            .getAddress
            .getHostAddress))
      (catch Exception e
        (log/warn (format "Unable to obtain %s interface address (%s)" iface, e))))))

(def ^{:doc "Looks up the ip address for the AS management interface."}
  management-interface-address
  (partial lookup-interface-address :management))

(def ^{:doc "Looks up the ip address for the AS public interface."}
  public-interface-address
  (partial lookup-interface-address :public))

(def ^{:doc "Looks up the ip address for the AS unsecure interface."}
  unsecure-interface-address
  (partial lookup-interface-address :unsecure))

(defn port
  "Returns the (possibly offset) port from the socket-binding in standalone.xml"
  [socket-binding-name]
  (if-let [sb (registry/get (str "jboss.binding." (name socket-binding-name)))]
    (.getAbsolutePort sb)))

(defn http-port
  "Returns the HTTP port for the embedded web server"
  []
  (port :http))

(defn hornetq-remoting-port
  "Returns the port that HornetQ is listening on for remote connections"
  []
  (port :messaging))

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

(defn require-resolve
  "Requires and resolves the given namespace-qualified symbol."
  [sym]
  (require (symbol (namespace sym)))
  (resolve sym))

(defn try-resolve
  "Tries to require and resolve the given namespace-qualified symbol, returning nil if not found."
  [sym]
  (try
    (require-resolve sym)
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

(defn try-import
  "Tries to import the given symbol, returning the class on success."
  [sym]
  (try
    (eval `(import (quote ~sym)))
    (catch Throwable _)))

(defmacro when-import
  "Executes body when sym is successfully imported"
  [sym & body]
  `(when (try-import ~sym)
     ~@body))

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

(defn wait-for
  "Waits for (t) to be true before invoking f, if passed. Evaluates
   test every 100 ms attempts times before giving up. Attempts
   defaults to 300. Passing :forever for attempts will loop until the
   end of time or (t) is true, whichever comes first."
  ([t]
     (wait-for t (constantly true)))
  ([t f]
     (wait-for t f 300))
  ([t f attempts]
     (let [wait #(Thread/sleep 100)]
       (cond
        (t)                   (f)
        (= :forever attempts) (do
                                (wait)
                                (recur t f :forever))
        (< attempts 0)        (throw (IllegalStateException.
                                      (str "Gave up waiting for " t)))
        :default              (do
                                (wait)
                                (recur t f (dec attempts)))))))

(defn wait-for-start
  "Waits for (.isStarted x) to be true before returning or invoking f."
  ([x]
     (wait-for-start x (constantly x)))
  ([x f]
     (wait-for-start x f 300))
  ([x f attempts]
     (wait-for #(.isStarted x) f attempts)))

(defn waiting-derefable
  "Returns an IDeref/IBlockingDeref that completes the deref and returns x when 
   (t) is true."
  [t x]
  (reify
    clojure.lang.IDeref
    (deref [_]
      (wait-for t (constantly x) :forever))
    clojure.lang.IBlockingDeref
    (deref [_ ms timeout-val]
      (try
        (wait-for t (constantly x) (int (/ ms 100)))
        (catch IllegalStateException _
          timeout-val)))))

(defn maybe-deref
  "derefs v if it is derefable, otherwise returns v"
  [v & args]
  (if (instance? IDeref v)
    (apply deref v args)
    v))

(defn profile-active?
  "Returns true if the leiningen profile is active"
  [id]
  (and (->> (registry/get :project)
            meta
            :active-profiles
            (some #{(keyword id)}))
       true))

(defn dev-mode?
  "Returns true if the app is running in dev mode."
  []
  (not (System/getenv "LEIN_NO_DEV")))

(defn set-bean-property
  "Calls a java bean-style setter (.setFooBar) for the given property (:foo-bar) and value."
  [bean prop value]
  (let [setter (->> (str/split (name prop) #"-")
                 (map str/capitalize)
                 (apply str ".set")
                 symbol)]
    ((eval `#(~setter %1 %2)) bean value)))

(defn validate-options*
  [name valid-keys opts]
  (if (::validated? opts)
    opts
    (do
      (doseq [k (keys opts)]
        (if-not (valid-keys k)
          (throw (IllegalArgumentException.
                   (format "%s is not a valid option for %s, valid options are: %s"
                     k name valid-keys)))))
      (assoc opts ::validated? true))))

(defmacro ^:no-doc validate-options
  "Validates that (keys opts) is a subset of :valid-options from (meta src)"
  ([src opts]
     `(validate-options ~src ~src ~opts))
  ([alt-name src opts]
     (let [src-var# (resolve src)]
       `(validate-options* (name (quote ~alt-name))
          (:valid-options (meta ~src-var#)) ~opts))))

(defn concat-valid-options
  "Grabs the :valid-options metadata from all of the passed vars, and concats them together into a set."
  [& vars]
  (set (mapcat #(-> % meta :valid-options) vars)))

(defn enum->map
  "Converts a java Enum that provides a value field into a map of value -> Enum instance."
  [enum]
  (->> enum
    EnumSet/allOf
    (map #(vector (.value %) %))
    (into {})))

(defn enum->keywords
  "Converts a java Enum that provides a value field into a list of those values as keywords.
   Auto-converts \"foo_bar\" to :foo-bar."
  [enum]
  (->> enum enum->map keys (map #(keyword (.replace % \_ \-)))))

(defn enum->set
  "Converts a java Enum that provides a value field into a set of keywords."
  [enum]
  (-> enum enum->keywords set))

(defn extract-options
  "Converts a clojure map into a WunderBoss options map."
  [m c]
  (let [optsm (enum->map c)]
    (->> m
      stringify-keys
      (map (fn [[k v]]
             (when-let [enum (optsm k (optsm (.replace k \- \_)))]
               [enum v])))
      (into {}))))

