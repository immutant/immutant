;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

(ns fnbox.build.tools
  (:import [org.apache.commons.io FileUtils])
  (:require [clojure.java.io :as io])
  (:require [clojure.java.shell :as shell])
  (:require [clojure.string :as str])
  (:require [clojure.zip :as zip])
  (:require [clojure.contrib.zip-filter :as zf])
  (:require [clojure.contrib.zip-filter.xml :as zfx])
  (:require [clojure.contrib.lazy-xml :as xml])
  (:require [org.satta.glob :as glob])
  (:use [clojure.pprint :only [pprint]]))

(defn unzip [zip-file dest-dir]
  (let [result (shell/sh "unzip" "-q" "-d" (str dest-dir) zip-file)]
    (if (not= (:exit result) 0)
      (println (str "ERROR: unzip failed: " (:err result))))
    (:out result)))

(defn with-message [message & body]
  (print (str message "... "))
  (flush)
  (doall body)
  (println "DONE"))

(defn print-err [& message]
  (comment (binding [*out* *err*]
             (apply println message))))

(defn xml-zip [path]
  (zip/xml-zip (xml/parse-trim path)))

(defn extract-versions [pom-path version-paths]
  (into {} (map (fn [[k path]]
                  [k (apply zfx/xml1-> (cons (xml-zip pom-path) (conj path zfx/text)))])
                version-paths)))

(defn init [assembly-root]
  (def root-dir (-> assembly-root .getCanonicalFile .getParentFile .getParentFile))
  (def root-pom (io/file root-dir "pom.xml"))
  (def build-dir (io/file (.getCanonicalFile assembly-root) "target/stage"))
  (def fnbox-dir (io/file build-dir "fnbox"))
  (def jboss-dir (io/file fnbox-dir "jboss"))

  (def version-paths {:fnbox [:version]
                      :jboss [:properties :version.jbossas]})

  (def versions (extract-versions root-pom version-paths))

  (def m2-repo (if (System/getenv "M2_REPO")
                 (System/getenv "M2_REPO")
                 (str (System/getenv "HOME") "/.m2/repository")))

  (def jboss-zip-file
    (str m2-repo "/org/jboss/as/jboss-as-dist/" (:jboss versions) "/jboss-as-dist-" (:jboss versions) ".zip"))

  (def fnbox-modules (reduce (fn [acc file]
                               (assoc acc
                                 (second (re-find #"fnbox-(.*)-module" (.getName file)))
                                 file))
                             {}
                             (glob/glob (str (.getAbsolutePath root-dir) "/modules/*/target/*-module")))))

(defn install-module [module-dir]
  (let [name (second (re-find #"fnbox-(.*)-module" (.getName module-dir)))
        dest-dir (io/file jboss-dir (str "modules/org/fnbox/" name "/main"))]
    (FileUtils/deleteQuietly dest-dir)
    (FileUtils/copyDirectory module-dir dest-dir)))

(defn backup-current-config [file]
  (let [to-file (io/file (.getParentFile file)
                         (str (first (str/split (.getName file) #"\.")) "-original.xml"))]
    (when-not (.exists to-file)
      (io/copy file to-file))))

(defn increase-deployment-timeout [loc]
  (zip/edit loc #(assoc-in % [:attrs :deployment-timeout] "1200")))

(defn add-extension [loc name]
  (let [module-name (str "org.fnbox." name)]
    (zip/append-child loc {:tag :extension :attrs {:module module-name}})))

(defn add-extensions [loc]
  (reduce add-extension loc (keys fnbox-modules)))

(defn add-subsystem [loc name]
  (let [module-name (str "urn:jboss:domain:fnbox-" name ":1.0")]
    (zip/append-child loc {:tag :subsystem :attrs {:xmlns module-name}})))

(defn add-subsystems [loc]
  (reduce add-subsystem loc (sort-by #(if (= "core" %) -1 1) (keys fnbox-modules))))

(defn add-system-property [loc prop value]
  (zip/append-child loc {:tag :property :attrs {:name prop :value value}}))

(defn replace-system-property [loc prop value]
  (zip/edit loc #(assoc % :attrs {:name prop :value value})))

(defn set-system-property [loc prop value]
  (if-let [child (zfx/xml1-> loc :property (zfx/attr= :name value))]
    (zip/up (replace-system-property child prop value))
    (add-system-property loc prop value)))

(defn unquote-cookie-path [loc]
  (set-system-property loc "org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR" "false"))

(defn set-welcome-root [loc]
  (if (= "false" (-> loc zip/node :attrs :enable-welcome-root))
    loc
    (zip/edit loc #(update-in % [:attrs :enable-welcome-root] (constantly "false")))))

(defn looking-at? [tag loc]
  (= tag (:tag (zip/node loc))))

(defn prepare-zip [file]
  "Make sure the doc has a <system-properties> element"
  (let [xml-zip (zip/xml-zip (xml/parse-trim file))]
    (if (zfx/xml1-> xml-zip zf/descendants :system-properties)
      xml-zip
      (zip/insert-right (zfx/xml1-> xml-zip :extensions) {:tag :system-properties}))))

(defn walk-the-doc [loc]
  (if (zip/end? loc)
    (zip/root loc)
    (recur (zip/next
            (cond
             (looking-at? :extensions loc) (add-extensions loc)
             (looking-at? :profile loc) (add-subsystems loc)
             (looking-at? :virtual-server loc) (set-welcome-root loc)
             (looking-at? :system-properties loc) (unquote-cookie-path loc)
             (looking-at? :jms-destinations loc) (zip/remove loc)
             (looking-at? :deployment-scanner loc) (increase-deployment-timeout loc)
             :else loc)))))
  
(defn transform-config [file]
  (let [in-file (io/file jboss-dir file)
        out-file (io/file (.getParentFile in-file) (str "fnbox/" (.getName in-file)))]
    (println (str "transforming " file))
    (io/make-parents out-file)
    (backup-current-config in-file)
    (io/copy (with-out-str
               (xml/emit
                (walk-the-doc (prepare-zip in-file))
                :indent 4))
             out-file)))
