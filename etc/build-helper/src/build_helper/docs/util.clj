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

(ns build-helper.docs.util
  (:require [clojure.java.io   :as io]
            [clojure.walk      :as walk]
            [codox.utils       :as cu]
            [codox.reader      :as cr]
            [codox.writer.html :as html]
            [hiccup.element    :refer [link-to]]
            [hiccup.core       :refer [h]])
  (:import java.io.File))

(defn generate-index [target-path src-paths]
  (let [index (io/file target-path "codox-index.clj")]
    (println "Writing doc index to" (.getCanonicalPath index) "...")
    (spit index
      (pr-str
        (walk/postwalk
          #(if (instance? File %) (.getCanonicalPath %) %)
          (-> (mapcat cr/read-namespaces src-paths)
            (cu/add-source-paths src-paths)))))))

(defn strip-prefix [root-path file-name]
  (.replace (.getCanonicalPath (io/file file-name))
    (.getCanonicalPath (io/file root-path)) ""))

(defn fix-path-entry [options entry]
  (if-let [f (:path entry)]
    (assoc entry :path (io/file (strip-prefix (:root-path options) f)))
    entry))

(defn hide-deftype-and-defrecord-factories [entry]
  (if (list? entry)
    (remove #(and (:doc %)
                  (or (.startsWith (:doc %) "Positional factory")
                      (.startsWith (:doc %) "Factory function"))) entry)
    entry))

(defn load-index [options file]
  (println "Processing" (.getAbsolutePath file))
  (walk/postwalk
   (fn [entry]
     (->> entry
       (fix-path-entry options)
       hide-deftype-and-defrecord-factories))
   (read-string (slurp file))))

(defn load-indexes
  ([options dir]
     (->> (file-seq (io/file dir))
          (filter #(= "codox-index.clj" (.getName %)))
          (mapcat (partial load-index options))))
  ([options dir & dirs]
     (sort-by :name
       (mapcat (partial load-indexes options) (cons dir dirs)))))

(def codox-options
  {:name "Immutant"
   :src-dir-uri "https://github.com/immutant/immutant/tree/"
   :src-linenum-anchor-prefix "L"
   :description "The public API for Immutant."})

(defn our-header [project]
  [:div#header
   [:h2 (link-to "http://immutant.org/" "Immutant Home")]
   [:h1 (link-to "index.html" (h (#'html/project-title project)))]])

(defn generate-docs [{:keys [version target-path base-dirs] :as options}]
  (let [target-dir (.getCanonicalPath (io/file target-path "apidocs"))]
    (println "Generating api docs to" target-dir "...")
    (with-redefs [html/header our-header]
      (-> codox-options
        (update-in [:src-dir-uri] str (if (re-find #"SNAPSHOT|incremental" version)
                                        "thedeuce"
                                        version))
        (assoc :version version
               :namespaces (apply load-indexes options base-dirs)
               :output-dir target-dir)
        (html/write-docs)))))
