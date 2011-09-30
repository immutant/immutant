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

(defn increase-deployment-timeout [xml]
  xml)

(defn add-extension [xml name]
  (let [module-name (str "org.fnbox." name)
        zip-xml (zip/xml-zip xml)]
    (if (zfx/xml1-> zip-xml :extensions :extension [(zfx/attr= :module module-name)])
      xml
      (zip/root (zip/append-child (zfx/xml1-> zip-xml :extensions) {:tag :extension :attrs {:module module-name}})))))

(defn add-extensions [xml]
  (reduce add-extension xml (keys fnbox-modules)))

(defn add-subsystem [xml name]
  (let [module-name (str "urn:jboss:domain:fnbox-" name ":1.0")]
    (if-let [profile (zfx/xml1-> (zip/xml-zip xml) zf/descendants :profile zip/down zip/rightmost
                                 [#(not= (zfx/attr % :xmlns) module-name)] zip/up)]
      (recur (zip/root (zip/append-child profile {:tag :subsystem :attrs {:xmlns module-name}}))
             name)
      xml)))

(defn add-subsystems [xml]
  (reduce add-subsystem xml (sort-by #(if (= "core" %) -1 1) (keys fnbox-modules))))

(defn set-welcome-root [xml]
  (if-let [loc (zfx/xml1-> (zip/xml-zip xml) zf/descendants :virtual-server [#(not= (zfx/attr % :enable-welcome-root) "false")])]
    (recur (zip/root (zip/edit loc #(assoc % :attrs (assoc (:attrs %) :enable-welcome-root "false")))))
    xml))

(defn add-system-properties-tag [xml]
  (let [xml-zip (zip/xml-zip xml)]
    (if (zfx/xml1-> xml-zip zf/descendants :system-properties)
      xml
      (zip/root (zip/insert-right (zfx/xml1-> xml-zip :extensions) {:tag :system-properties})))))

(defn add-system-property [xml prop value]
  (zip/root (zip/append-child (zfx/xml1-> (zip/xml-zip xml) :system-properties) {:tag :property :attrs {:name prop :value value}})))

(defn replace-system-property [xml prop value]
  (zip/root (zip/edit (zfx/xml1-> (zip/xml-zip xml) :system-properties :property [(zfx/attr= :name prop)])
                      #(assoc % :attrs {:name prop :value value}))))

(defn add-or-replace-system-property [xml prop value]
  (if (zfx/xml1-> (zip/xml-zip xml) :system-properties :property [(zfx/attr= :name value)])
    (replace-system-property xml prop value)
    (add-system-property xml prop value)))

(defn set-system-property [xml prop value]
  (-> xml
      add-system-properties-tag
      (add-or-replace-system-property prop value)))

(defn unquote-cookie-path [xml]
  (set-system-property xml "org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR" "false"))

(defn transform-config [file]
  (let [in-file (io/file jboss-dir file)
        xml (xml/parse-trim in-file)
        out-file (io/file (.getParentFile in-file) (str "fnbox/" (.getName in-file)))]
    (print-err (str "transforming " file))
    (io/make-parents out-file)
    (backup-current-config in-file)
    (io/copy (with-out-str
               (xml/emit
                (-> xml
                    increase-deployment-timeout
                    add-extensions
                    add-subsystems
                    set-welcome-root
                    unquote-cookie-path)
                :indent 4))
             out-file)))
