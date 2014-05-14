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
            [clojure.string :as str]
            [clojure.walk      :as walk]
            [codox.utils       :as cu]
            [codox.reader.clojure :as cr]
            [codox.writer.html :as html]
            [hiccup.element    :refer [link-to javascript-tag unordered-list]]
            [hiccup.core       :refer [h]]
            [hiccup.page       :as hp]
            [markdown.core     :as md])
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

(defn fn->code-link [namespace text state]
  [(str/replace text #"\{\{(.*?)\}\}"
     (fn [[_ v]]
       (let [parts (str/split v #"/")
             [ns var] (if (> (count parts) 1) parts [(:name namespace) (first parts)])]
         (format "<code><a href=\"%s.html#var-%s\">%s</a></code>" ns var var))))
   state])

(defn keyword->code [text state]
  [(str/replace text #"(\s+|>|^)(:\S+)"
     (fn [[_ prefix kw]]
       (format "%s<code>%s</code>" prefix kw))) state])

(defn default->em [text state]
  [(str/replace text #"(\[.*?\])(:?\s*)$"
     (fn [[_ default postfix]]
       (format "<em>%s</em>%s" default postfix))) state])

(defn md->html [namespace content]
  (md/md-to-html-string content
    :custom-transformers [(partial fn->code-link namespace) keyword->code default->em]))



;; pulled from codox so we can easily apply markdown to the docstrings

(def default-includes
  (list
    [:meta {:charset "UTF-8"}]
    (hp/include-css "css/default.css")
    (hp/include-css "css/docs.css")
    (hp/include-js "js/jquery.min.js")
    (hp/include-js "js/page_effects.js")))

(defn header [project]
  [:div#header
   [:h2 (link-to "http://immutant.org/" "Immutant Home")]
   [:h1 (link-to "index.html" (h (#'html/project-title project)))]])

(defn- var-docs [namespace var & [source-link]]
  [:div.public.anchor {:id (h (#'html/var-id var))}
   [:h3 (h (:name var))]
   (if-not (= (:type var) :var)
     [:h4.type (name (:type var))])
   (if-let [added (:added var)]
     [:h4.added "added in " added])
   (if-let [deprecated (:deprecated var)]
     [:h4.deprecated "deprecated" (if (string? deprecated) (str " in " deprecated))])
   [:div.usage
    (for [form (#'html/var-usage var)]
      [:code (h (pr-str form))])]
   [:div.doc (md->html namespace (:doc var))]
   (if-let [members (seq (:members var))]
     [:div.members
      [:h4 "members"]
      [:div.inner (map (partial var-docs namespace) members)]])
   (if source-link [:div.src-link source-link])])

(defn- namespace-page [project namespace]
  (hp/html5
    [:head
     default-includes
     [:title (h (:name namespace)) " documentation"]]
   [:body
    (header project)
    (#'html/namespaces-menu project namespace)
    (#'html/vars-menu namespace)
    [:div#content.namespace-docs
     [:h2#top.anchor (h (:name namespace))]
     [:div.doc (md->html namespace (:doc namespace))]
     (for [var (#'html/sorted-public-vars namespace)]
       (var-docs namespace var (#'html/var-source-link project var)))]]))

(defn- index-page [project]
  (hp/html5
   [:head
    default-includes
    [:title (h (#'html/project-title project)) " API documentation"]]
   [:body
    (header project)
    (#'html/namespaces-menu project)
    [:div#content.namespace-index
     [:h2 (h (#'html/project-title project))]
     [:div.doc (h (:description project))]
     (for [namespace (sort-by :name (:namespaces project))]
       [:div.namespace
        [:h3 (link-to (#'html/ns-filename namespace) (h (:name namespace)))]
        [:div.doc (md->html namespace (:doc namespace))]
        [:div.index
         [:p "Public variables and functions:"]
         (unordered-list
           (for [var (#'html/sorted-public-vars namespace)]
             (link-to (#'html/var-uri namespace var) (h (:name var)))))]])]]))

;; end pullage

(defn cp [rsrc dest]
  (-> rsrc
    io/resource
    io/input-stream
    (io/copy (io/file dest rsrc))))

(defn generate-docs [{:keys [version target-path base-dirs] :as options}]
  (let [target-dir (.getCanonicalPath (io/file target-path "apidocs"))]
    (println "Generating api docs to" target-dir "...")
    (with-redefs [html/namespace-page namespace-page
                  html/index-page index-page
                  html/var-docs var-docs]
      (-> codox-options
        (update-in [:src-dir-uri] str (if (re-find #"SNAPSHOT|incremental" version)
                                        "thedeuce"
                                        version))
        (assoc :version version
               :namespaces (apply load-indexes options base-dirs)
               :output-dir target-dir)
        (html/write-docs)))
    (cp "css/docs.css" target-dir)))
