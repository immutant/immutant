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

(ns ^{:no-doc true} immutant.web.middleware
    (:use ring.middleware.stacktrace
          ring.middleware.reload)
    (:require [immutant.registry :as registry]
              [immutant.util     :as util]
              [clojure.java.io :as io]
              dynapath.util))

(def ^{:dynamic true} *dev*)

(defn auto-reload?
  "Automatically reload source files?"
  [options]
  (:auto-reload? options *dev*))

(defn stacktraces?
  "Show stacktraces?"
  [options]
  (:stacktraces? options *dev*))

(defn reload-paths
  "Default reload-paths to all directories in the classpath, whether
  they exist at time of deployment or not"
  [options]
  (let [cp (map io/file (dynapath.util/all-classpath-urls (clojure.lang.RT/baseLoader)))
        dirs (map str (remove #(and (.exists %) (not (.isDirectory %))) cp))]
    (:reload-paths options dirs)))

(defn add-stacktraces [handler options]
  (if (stacktraces? options)
    (wrap-stacktrace handler)
    handler))

(defn add-auto-reload [handler options]
  (if (auto-reload? options)
    (wrap-reload handler {:dirs (reload-paths options)})
    handler))

(defn add-middleware
  [handler options]
  (binding [*dev* (util/dev-mode?)]
    (-> handler
        (add-auto-reload options)
        (add-stacktraces options))))