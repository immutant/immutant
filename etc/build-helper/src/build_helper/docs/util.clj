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
            [robert.hooke         :as bob]
            [hiccup.core          :as hc]
            [hiccup.element       :as he]
            [clostache.parser     :as cp])
  (:import java.io.File
           [org.pegdown Extensions PegDownProcessor]))

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
  (if (:doc entry)
    (assoc entry :doc/format :markdown)
    entry))

(defn massage-docstring [entry]
  (if (:doc entry)
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
  (str/replace content #"</head>" "<link href=\"assets/immutant.css\" rel=\"stylesheet\" type=\"text/css\"></head>"))

(defn render-with-post-processing [f & args]
  (-> (apply f args)
    add-custom-css-ref))

(def pegdown
  (PegDownProcessor.
   (bit-or Extensions/AUTOLINKS
           Extensions/QUOTES
           Extensions/SMARTS
           Extensions/STRIKETHROUGH
           Extensions/TABLES
           Extensions/FENCED_CODE_BLOCKS
           Extensions/WIKILINKS
           Extensions/DEFINITIONS
           Extensions/ABBREVIATIONS)
   2000))

(defn render-markdown [guide namespaces content]
  (.markdownToHtml pegdown content (#'html/link-renderer {:namespaces namespaces} (:base-ns guide))))

(defn guides-menu [guides]
  [:div#guides
   [:h3 (he/link-to "index.html" [:span.inner "Guides"])]
   [:ul
    (for [{:keys [title output-file]} (sort-by :sequence guides)]
      [:li {:class (str "guide " title "-current")}
       (he/link-to output-file [:div.inner [:span title]])])]])

(defn make-template
  [target-dir]
  (-> (slurp (io/file target-dir "index.html"))
    (str/replace
      #"(<div class=\"namespace-index\" id=\"content\">).*?(</body>)"
      "<div class='namespace-index guide' id='content'><div class='markdown doc'>{{CONTENT}}</div></div></body>")))

(defn add-content-header [{:keys [title description]} content]
  (hc/html
    [:div.guide-content
     [:h1 (str title " Guide")]
     [:div.description [:span description]]
     content]))

(defn wrap-template [guides current target-dir content]
  (-> (make-template target-dir)
    (str/replace "{{CONTENT}}" content)
    (str/replace (format "%s-current" (:title current)) "current")
    (str/replace  "</body>"
      (str "<script src=\"assets/jquery.syntax.min.js\" type=\"text/javascript\"></script>"
        "<script src=\"assets/immutant.js\" type=\"text/javascript\"></script></body>"))))

(def ^:dynamic *guides*)

(defn add-guides-menu [f & args]
  (let [menu (apply f args)]
    (apply vector
      (first menu)
      (guides-menu *guides*)
      (rest menu))))

(defn reduce-toc
  ([headings]
     (reduce-toc [] headings))
  ([reduced headings]
     (if-let [curr (first headings)]
       (reduce-toc
         (let [prior (last reduced)]
           (if (or (not prior)
                 (= (:level curr) (:level prior)))
             (conj reduced curr)
             (conj (vec (butlast reduced))
               (update-in prior [:children] #(conj (vec %) curr)))))
         (rest headings))
       reduced)))



(declare toc-children)

(defn toc-li
  [{:keys [id text children]}]
  [:li
   (he/link-to (str \# id) [:span text])
   (toc-children children)])

(defn toc-children [children]
  (when (seq children)
     [:ul
      (for [child children]
        (toc-li child))]))

(defn generate-toc [headings]
  (let [toc (reduce-toc headings)]
    (hc/html
      [:div#toc
       (toc-children toc)])))

(defn add-toc [{:keys [toc]} content]
  (if toc
    (let [headings (atom [])
          adjusted-content (str/replace
                             content
                             #"<h([2-3])>(.*?)</h[2-3]>"
                             (fn [[_ level text]]
                               (let [id (gensym "h")]
                                 (swap! headings conj {:level (read-string level) :text text :id id})
                                 (format "<h%s id=\"%s\">%s</h%s>" level id text level))))]
      (str (generate-toc @headings) adjusted-content))
    content))

(defn parse-guide [file]
  (let [[_ metadata & content] (str/split (slurp file) #"---")
        filename (.getName file)]
    (merge
      {:toc true}
      (assoc (read-string metadata)
        :source-file file
        :content (str/join content)
        :output-file (str "guide-" (subs filename 0 (- (.length filename) 2)) "html")))))

(defn parse-guides [guide-dir]
  (for [f (file-seq guide-dir)
        :when (.endsWith (.getName f) ".md")]
    (parse-guide f)))

(defn add-wunderboss-tag [{:keys [versions] :as options}]
  (assoc options
    :wunderboss-tag (let [version (versions 'org.projectodd.wunderboss)]
                      (if (re-find #"^\d+\.\d+\.\d+$" version)
                        version
                        "master"))))

(defn add-mustache-data [options]
  (-> options
    add-wunderboss-tag))

(defn render-guides [options guides target-dir]
  (doseq [{:keys [source-file content output-file] :as guide} guides]
    (println "Rendering" (.getName source-file) "to" (str target-dir "/" output-file))
    (->>
      (cp/render content (add-mustache-data options))
      (render-markdown guide (:namespaces options))
      (add-toc guide)
      (add-content-header guide)
      (wrap-template guides guide target-dir)
      (spit (io/file target-dir output-file)))))

(defn guide-index [guides]
  (hc/html
    [:div#guide-index.markdown.doc
     [:h2.shrunk "Guides"]
     [:table
      (for [{:keys [title description output-file]} (sort-by :sequence guides)]
        [:tr
         [:td
          (he/link-to output-file [:span title])]
         [:td [:span.description description]]])]]
    [:h2.shrunk "Namespaces"]))

(defn index-with-guide-index [f & args]
  (str/replace (apply f args)  #"(id=\"content\"><h2>.*?</h2>)" (str "$1" (guide-index *guides*))))

(def codox-options
  {:name "Immutant"
   :src-dir-uri "https://github.com/immutant/immutant/tree/"
   :src-linenum-anchor-prefix "L"
   :description "The public API for Immutant."})

(defn cp [rsrc dest]
  (let [dest-file (io/file dest rsrc)]
    (.mkdirs (.getParentFile dest-file))
    (-> rsrc
      io/resource
      io/input-stream
      (io/copy dest-file))))

(defn generate-docs [{:keys [version target-path base-dirs guides-dir] :as options}]
  (bob/add-hook #'html/format-doc #'format-doc-with-post-processing)
  (bob/add-hook #'html/index-page #'render-with-post-processing)
  (bob/add-hook #'html/index-page #'index-with-guide-index)
  (bob/add-hook #'html/namespace-page #'render-with-post-processing)
  (bob/add-hook #'html/namespaces-menu #'add-guides-menu)

  (let [target-dir (.getCanonicalPath (io/file target-path "apidocs"))
        guides (parse-guides (io/file guides-dir))
        namespaces (apply load-indexes options base-dirs)]
    (binding [*guides* guides]
      (println "Generating api docs to" target-dir "...")
      (-> codox-options
        (update-in [:src-dir-uri] str (if (re-find #"SNAPSHOT|incremental" version)
                                        "thedeuce"
                                        version))
        (assoc :version version
               :namespaces namespaces
               :output-dir target-dir)
        (html/write-docs))
      (doseq [f ["assets/immutant.css"
                 "assets/immutant.js"
                 "assets/jquery.syntax.brush.lisp.js"
                 "assets/jquery.syntax.cache.js"
                 "assets/jquery.syntax.core.css"
                 "assets/jquery.syntax.core.js"
                 "assets/jquery.syntax.js"
                 "assets/jquery.syntax.layout.plain.css"
                 "assets/jquery.syntax.layout.plain.js"
                 "assets/jquery.syntax.min.js"]]
        (cp f target-dir))
      (render-guides
        (assoc options :namespaces namespaces)
        guides target-dir))))
