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

(ns immutant.runtime
  "This namespace is solely for use during the application runtime
bootstrapping process. Applications shouldn't use anything here."
  (:require [clojure.string             :as str]
            [clojure.java.io            :as io]
            [clojure.tools.logging      :as log]
            [dynapath.dynamic-classpath :as dc]
            [immutant.repl              :as repl]
            [immutant.util              :as util]
            [immutant.registry          :as registry]))

(defn ^{:internal true} require-and-invoke 
  "Takes a string of the form \"namespace/fn\", requires the namespace, then invokes fn"
  [namespaced-fn & [args]]
  (let [[namespace function] (map symbol (str/split namespaced-fn #"/"))]
    (require namespace)
    (apply (intern namespace function) args)))

(defn ^{:private true} dynapathize-class-loader []
  (extend-type org.immutant.core.ImmutantClassLoader
     dc/DynamicClasspath
     (can-read? [_] true)
     (can-add? [_] false)
     (classpath-urls [cl] (seq (.getResourcePaths cl)))))

(defn ^{:internal true} initialize 
  "Attempts to initialize the app by calling an init-fn (if given) or, lacking that,
tries to load the immutant.init namespace. In either case,
post-initialize is called to finalize initialization."
  [init-fn config-hash]

  (dynapathize-class-loader)
  
  (if init-fn
    (do
      (log/info "Initializing " (util/app-name) "via" init-fn)
      (require-and-invoke init-fn))
    (try (println "ns") (require 'immutant.init)
         (catch java.io.FileNotFoundException _
           (log/warn "No :init fn or immutant.init namespace found for"
                     (util/app-name)
                     "- no initialization will be performed"))))
  
  (repl/init-repl (into {} config-hash)))

(defn ^{:internal true} set-app-config
  "Takes the full application config and project map as data strings
and makes them available as data under the :config and :project keys
in the registry."
  [config project]
  (registry/put :config (read-string config))
  (registry/put :project (read-string project)))


