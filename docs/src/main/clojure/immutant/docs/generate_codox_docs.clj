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

(ns immutant.docs.generate-codox-docs
  (:require [clojure.java.io      :as io]
            [clojure.walk         :as walk]
            [codox.writer.html    :as html])
  (:use [hiccup core page element])
  (:gen-class))

(def ^{:dynamic true} *root-path*)

(defn strip-prefix [file-name]
  (.replace (.getCanonicalPath (io/file file-name))
            (.getCanonicalPath (io/file *root-path*)) ""))

(defn fix-path-entry [entry]
  (if-let [f (:path entry)]
    (assoc entry :path (io/file (strip-prefix f)))
    entry))

(defn hide-deftype-and-defrecord-factories [entry]
  (if (list? entry)
    (remove #(and (:doc %)
                  (or (.startsWith (:doc %) "Positional factory")
                      (.startsWith (:doc %) "Factory function"))) entry)
    entry))

(defn load-index [file]
  (println "Processing" (.getAbsolutePath file))
  (walk/postwalk
   (fn [entry]
     (-> entry
         fix-path-entry
         hide-deftype-and-defrecord-factories))
   (read-string (slurp file))))

(defn load-indexes
  ([dir]
     (->> (file-seq (io/file dir))
          (filter #(= "codox-index.clj" (.getName %)))
          (mapcat load-index)))
  ([dir & dirs]
     (sort-by :name
              (mapcat load-indexes (cons dir dirs)))))

(def options
  {:output-dir "target/html/apidoc"
   :name "Immutant"
   :src-dir-uri "https://github.com/immutant/immutant/tree/"
   :src-linenum-anchor-prefix "L"
   :description "The public API for Immutant."
   ;;:copyright "Copyright 2011-2012 Red Hat, Inc."
   })

(defn our-header [project]
  [:div#header
   [:h2 (link-to "../index.html" "Immutant Manual")]
   [:h2 (link-to "http://immutant.org/" "Immutant Home")]
   [:h1 (link-to "index.html" (h (#'html/project-title project)))]])

(defn -main [root-path version & base-dirs]
  (println "Generating api docs...")
  (binding [*root-path* root-path]
    (with-redefs [html/header our-header]
      (-> options
          (update-in [:src-dir-uri] str (if (re-find #"SNAPSHOT|incremental" version)
                                          "master"
                                          version))
          (assoc :version version
                 :namespaces (->> (apply load-indexes base-dirs)
                                  (remove :no-doc)))
          (html/write-docs))))
  (shutdown-agents))
