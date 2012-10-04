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
  (:require [clojure.string        :as str]
            [clojure.java.io       :as io]
            [clojure.tools.logging :as log]
            [immutant.repl         :as repl]
            [immutant.utilities    :as util]
            [immutant.registry     :as registry]))

(defn ^{:internal true} require-and-invoke 
  "Takes a string of the form \"namespace/fn\", requires the namespace, then invokes fn"
  [namespaced-fn & [args]]
  (let [[namespace function] (map symbol (str/split namespaced-fn #"/"))]
    (require namespace)
    (apply (intern namespace function) args)))

(defn ^{:private true} bultitudinize-class-loader []
   (when-let [p (util/try-resolve 'bultitude.core/ProvidesClasspath)]
     (log/info
      "bultitude.core/ProvidesClasspath is available, implementing for ImmutantClassLoader")
     (extend org.immutant.core.ImmutantClassLoader
       @p 
       {:get-classpath #(map io/as-file (.getResourcePaths %))})))

(defn ^{:private true} post-initialize
  [config]
  (bultitudinize-class-loader)
  (repl/init-repl config))

(defn ^{:internal true} initialize 
  "Attempts to initialize the app by calling an init-fn (if given) or, lacking that,
tries to load an immutant.clj from the app-root. In either case, post-initialize is called
to finalize initialization."
  [init-fn config-hash]
  (let [init-file (util/app-relative "immutant.clj")
        init-file-exists (.exists init-file)]
    (if init-fn
      (do
        (if init-file-exists
          (log/warn "immutant.clj found in" (util/app-name) ", but you specified an :init fn; ignoring immutant.clj"))
        (require-and-invoke init-fn))
      (if init-file-exists
        (load-file (.getAbsolutePath init-file))
        (log/warn "no immutant.clj found in" (util/app-name) "and you specified no init fn; no app initialization will be performed"))))
  (post-initialize (into {} config-hash)))

(defn ^{:internal true} set-app-config
  "Takes the full application config and project map as data strings and makes them available as data under the :config and :project keys in the registry."
  [config project]
  (registry/put :config (read-string config))
  (registry/put :project (read-string project)))


