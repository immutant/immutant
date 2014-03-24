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
  (:require [immutant.util :as u]
            [immutant.wildfly.repl :as repl]
            [clojure.java.io :as io])
  (:import org.jboss.modules.ModuleClassLoader
           java.net.URL))

(defn- get-resource-loaders
  [cl]
  (-> (doto (.getDeclaredMethod ModuleClassLoader "getResourceLoaders"
              (make-array Class 0))
        (.setAccessible true))
    (.invoke cl (make-array Class 0))))

(defn- if-exists?
  "Returns the given url if it matches a file that exists."
  [url]
  (if (.exists (io/file url))
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

(defmacro extend-module-classloader []
  (if (u/try-resolve 'clojure.java.classpath/URLClasspath)
    `(extend-protocol clojure.java.classpath/URLClasspath
       ModuleClassLoader
       (urls [loader#]
         (get-module-loader-urls loader#)))))

(defn init [init-fn opts]
  (extend-module-classloader)
  (require (symbol (namespace init-fn)))
  ((resolve init-fn))
  (if-let [nrepl (:nrepl opts)] (repl/start nrepl)))
