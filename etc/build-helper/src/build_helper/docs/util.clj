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
  (:require [clojure.java.io      :as io]
            [clojure.string       :as str]
            [clojure.walk         :as walk]
            [codox.utils          :as cu]
            [codox.reader.clojure :as cr]
            [codox.writer.html    :as html]
            hiccup.page
            [robert.hooke         :as bob])
  (:import java.io.File))

(defn generate-index [root-path target-path src-paths]
  (let [index (io/file target-path "codox-index.clj")]
    (println "Writing doc index to" (.getCanonicalPath index) "...")
    (spit index
      (pr-str
        (walk/postwalk
          #(if (instance? File %) (.getCanonicalPath %) %)
          (-> (mapcat cr/read-namespaces src-paths)
            (cu/add-source-paths root-path src-paths)))))))

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

(defn fn-link->code [text]
  (when text
    (str/replace text #"(\[\[.*?\]\])"
      (fn [[_ link]]
        (format "<code>%s</code>" link)))))

(defn set-format-on-vars [entry]
  (if (map? entry)
    (assoc entry :doc/format :markdown)
    entry))

(defn massage-docstring [entry]
  (if (map? entry)
    (update-in entry [:doc] fn-link->code)
    entry))

(defn load-index [options file]
  (println "Processing" (.getAbsolutePath file))
  (walk/postwalk
   (fn [entry]
     (->> entry
       (fix-path-entry options)
       hide-deftype-and-defrecord-factories
       set-format-on-vars
       massage-docstring))
   (read-string (slurp file))))

(defn load-indexes
  ([options dir]
     (->> (file-seq (io/file dir))
          (filter #(= "codox-index.clj" (.getName %)))
          (mapcat (partial load-index options))))
  ([options dir & dirs]
     (sort-by :name
       (mapcat (partial load-indexes options) (cons dir dirs)))))

(defn keyword->code [text]
  (when text
    (str/replace text #"([^a-zA-Z0-9])(:[:0-9a-zA-Z?+_?-]+)"
      (fn [[_ prefix kw]]
        (format "%s<code>%s</code>" prefix kw)))))

;; we have to wrap keywords as code *after* markdown processing, else
;; the graves-within-graves confuses things
(defn format-doc-with-post-processing [f project ns var]
  (update-in (f project ns var) [1] keyword->code))

(defn add-custom-css-ref [content]
  (str/replace content #"</head>" "<link href=\"css/docs.css\" rel=\"stylesheet\" type=\"text/css\"></head>"))

(defn render-with-post-processing [f & args]
  (-> (apply f args)
    add-custom-css-ref))

(def codox-options
  {:name "Immutant"
   :src-dir-uri "https://github.com/immutant/immutant/tree/"
   :src-linenum-anchor-prefix "L"
   :description "The public API for Immutant."})

(defn cp [rsrc dest]
  (-> rsrc
    io/resource
    io/input-stream
    (io/copy (io/file dest rsrc))))

(defn generate-docs [{:keys [version target-path base-dirs] :as options}]
  (bob/add-hook #'html/format-doc #'format-doc-with-post-processing)
  (bob/add-hook #'html/index-page #'render-with-post-processing)
  (bob/add-hook #'html/namespace-page #'render-with-post-processing)

  (let [target-dir (.getCanonicalPath (io/file target-path "apidocs"))]
    (println "Generating api docs to" target-dir "...")
    (-> codox-options
      (update-in [:src-dir-uri] str (if (re-find #"SNAPSHOT|incremental" version)
                                      "thedeuce"
                                      version))
      (assoc :version version
             :namespaces (apply load-indexes options base-dirs)
             :output-dir target-dir)
      (html/write-docs))
    (cp "css/docs.css" target-dir)))
