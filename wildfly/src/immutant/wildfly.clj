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

(ns immutant.wildfly
  "Utility functions only useful within a WildFly container."
  (:require [immutant.internal.util :as u]
            [immutant.wildfly.repl  :as repl]
            [wunderboss.util        :as wu]
            [clojure.java.io        :as io])
  (:import java.net.URL
           org.projectodd.wunderboss.WunderBoss))

(def module-class-loader-class (memoize #(u/try-import 'org.jboss.modules.ModuleClassLoader)))

(defn in-container?
  "Returns true if the application is currently running inside WildFly."
  []
  (boolean (get (WunderBoss/options) "wildfly-service")))

(defn- get-resource-loaders
  [cl]
  (if (module-class-loader-class)
    (-> (doto (.getDeclaredMethod (module-class-loader-class) "getResourceLoaders"
                (make-array Class 0))
          (.setAccessible true))
      (.invoke cl (make-array Class 0)))))

(defn- if-exists?
  "Returns the given url if it matches a file that exists."
  [url]
  (if (and url (.exists (io/file url)))
    url))

(defn- loader->url
  "Converts a ResourceLoader into a url."
  [l]
  (if-let [r (.getResource l "/")]
    (.getURL r)))

(defn- vfs->file
  "Converts a vfs: url to a file: url."
  [url]
  (if-let [match (and url (re-find #"^vfs(:.*)" (.toExternalForm url)))]
    (URL. (str "file" (last match)))
    url))

(defn- get-module-loader-urls [loader]
  (->> loader
    get-resource-loaders
    (map (comp if-exists? vfs->file loader->url))
    (keep identity)))

(defmacro ^:no-doc extend-module-classloader-to-cjc []
  (if (u/try-resolve 'clojure.java.classpath/URLClasspath)
    `(extend-type (module-class-loader-class)
       clojure.java.classpath/URLClasspath
       (urls [cl#]
         (get-module-loader-urls cl#)))))

(defmacro ^:no-doc extend-module-classloader-to-dynapath []
  (if (u/try-resolve 'dynapath.dynamic-classpath/DynamicClasspath)
    `(extend-protocol dynapath.dynamic-classpath/DynamicClasspath
       (module-class-loader-class)
       (can-read? [cl#] true)
       (can-add? [cl#] false)
       (classpath-urls [cl#]
         (get-module-loader-urls cl#)))))

(defn ^:no-doc init-deployment
  "Initializes an in-container deployment. Should be used by the
  'init' key in the deployment properties to initialize the app."
  [init-fn opts]
  (extend-module-classloader-to-cjc)
  (extend-module-classloader-to-dynapath)
  ((u/require-resolve init-fn))
  (if-let [nrepl (:nrepl opts)] (repl/start nrepl)))

(defn get-from-service-registry [k]
  (if-let [registry (wu/service-registry)]
    (if-let [servicename-class (u/try-import 'org.jboss.msc.service.ServiceName)]
      (.getService registry
        (if-let [service (if (instance? servicename-class k)
                           k
                           (. servicename-class parse k))]
          (.getValue service))))))

(defn port
  "Returns the (possibly offset) port from the socket-binding in standalone.xml"
  [socket-binding-name]
  (if-let [sb (get-from-service-registry (str "jboss.binding." (name socket-binding-name)))]
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
  ;; TODO: figure out where to store/get the web-context
  (if-let [context "";(immutant.registry/get "web-context")
           ]
    (.getName context)))

(defn app-uri
  "Returns the base URI for the app, given a host [localhost]"
  [& [host]]
  (let [host (or host "localhost")]
    (str "http://" host ":" (http-port) (context-path))))
