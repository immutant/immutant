;; Copyright 2014-2016 Red Hat, Inc, and individual contributors.
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
  "Utility functions only useful within a [WildFly](http://wildfly.org/) container."
  (:require [immutant.internal.util :as u]
            [wunderboss.util        :as wu]
            [clojure.java.io        :as io])
  (:import java.net.URL
           org.projectodd.wunderboss.WunderBoss))

(defmacro ^:no-doc ignore-load-failures [& body]
  `(try
     ~@body
     (catch ClassNotFoundException _#)
     (catch NoClassDefFoundError _#)))

(defn- if-exists?
  "Returns the given url if it matches a file that exists."
  [url]
  (when (and url (.exists (io/file url)))
    url))

(defn- loader->url
  "Converts a ResourceLoader into a url."
  [l]
  (when-let [r (.getResource l "/")]
    (.getURL r)))

(defn- vfs->file
  "Converts a vfs: url to a file: url."
  [^URL url]
  (if-let [match (and url (re-find #"^vfs(:.*)" (.toExternalForm url)))]
    (URL. (str "file" (last match)))
    url))

(def ^:no-doc ^Class module-class-loader-class
  (memoize #(u/try-import 'org.jboss.modules.ModuleClassLoader)))

(defn- get-resource-loaders
  [cl]
  (when (module-class-loader-class)
    (-> (doto (.getDeclaredMethod (module-class-loader-class) "getResourceLoaders"
                (make-array Class 0))
          (.setAccessible true))
      (.invoke cl (make-array Class 0)))))

(defn ^:no-doc get-module-loader-urls [loader]
  (->> loader
    get-resource-loaders
    (map (comp if-exists? vfs->file loader->url))
    (keep identity)))

(defn ^:no-doc extend-module-classloader-to-cjc []
  (when (u/try-resolve 'clojure.java.classpath/URLClasspath)
    (eval
      `(extend-protocol clojure.java.classpath/URLClasspath
         (module-class-loader-class)
         (urls [cl#]
           (get-module-loader-urls cl#))))))

(defn ^:no-doc extend-module-classloader-to-dynapath []
  (when (u/try-resolve 'dynapath.dynamic-classpath/DynamicClasspath)
    (eval
      `(extend-protocol dynapath.dynamic-classpath/DynamicClasspath
         (module-class-loader-class)
         (can-read? [cl#] true)
         (can-add? [cl#] false)
         (classpath-urls [cl#]
           (get-module-loader-urls cl#))))))

(defn ^:no-doc init-deployment
  "Initializes an in-container deployment. Should be used by the
  'init' key in the deployment properties to initialize the app."
  [init-fn opts]
  (extend-module-classloader-to-cjc)
  (extend-module-classloader-to-dynapath)
  (if init-fn
    ((u/require-resolve init-fn))
    (u/warn "No init function provided; no initialization performed."))
  (when-let [nrepl (:nrepl opts)]
    ((u/require-resolve 'immutant.wildfly.repl/start) nrepl)))

(defn get-from-service-registry
  "Looks up a service in the WildFly internal service registry."
  [k]
  (when-let [registry (wu/service-registry)]
    (when-let [servicename-class (u/try-import 'org.jboss.msc.service.ServiceName)]
      (when-let [service (.getService registry
                           (if (instance? servicename-class k)
                             k
                             (eval `(ServiceName/parse ~k))))]
        (.getValue service)))))

(defn port
  "Returns the (possibly offset) port from the socket-binding in standalone.xml"
  [socket-binding-name]
  (when-let [sb (get-from-service-registry (str "jboss.binding." (name socket-binding-name)))]
    (.getAbsolutePort sb)))

(def http-port
  "Returns the HTTP port for the embedded web server"
  (partial port :http))

(defn- invoke-as-util-method [^String method]
  (ignore-load-failures
    (-> ^Class (u/try-import 'org.projectodd.wunderboss.as.ASUtils)
      (.getMethod method nil)
      (.invoke nil nil))))

(let [container-type (delay (invoke-as-util-method "containerTypeAsString"))]
  (defn in-eap?
  "Returns true if we're in an EAP container."
  []
  (= "EAP" @container-type)))

(let [streaming-supported? (delay (boolean (invoke-as-util-method "isAsyncStreamingSupported")))]
  (defn async-streaming-supported?
  "Returns true if the container supports async HTTP stream sends."
  []
  @streaming-supported?))

(defn messaging-remoting-port
  "Returns the port that HornetQ is listening on for remote connections"
  []
  (port (if (in-eap?) :messaging :http)))

(defn context-path
  "Returns the HTTP context path for the deployed app"
  []
  (get (WunderBoss/options) "servlet-context-path"))

(defn base-uri
  "Returns the base URI for the deployment, given a `host` [localhost] and `protocol` [http]"
  ([]
     (base-uri "localhost"))
  ([host]
     (base-uri host "http"))
  ([host protocol]
     (format "%s://%s:%s%s" protocol host (http-port) (context-path))))

(let [in-cluster (delay (invoke-as-util-method "inCluster"))]
  (defn in-cluster?
  "Returns true if running inside a cluster"
  []
  @in-cluster))
