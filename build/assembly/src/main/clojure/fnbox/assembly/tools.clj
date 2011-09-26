(ns fnbox.assembly.tools
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
  (comment
    (print (str message "... "))
    (flush)
    (doall body)
    (println "DONE")))

(defn print-err [& message]
  (binding [*out* *err*]
    (apply println message)))

(defn xml-zip [path]
  (zip/xml-zip (xml/parse-trim path)))

(defn extract-versions [pom-path version-paths]
  (into {} (map (fn [[k path]]
                  [k (apply zfx/xml1-> (cons (xml-zip pom-path) (conj path zfx/text)))])
                version-paths)))

(def base-dir (io/file (System/getProperty "user.dir")))
(def root-dir (-> base-dir .getParentFile .getParentFile))
(def root-pom (io/file root-dir "pom.xml"))

(def build-dir (io/file base-dir "target/stage"))
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
                           (glob/glob (str (.getAbsolutePath root-dir) "/modules/*/target/*-module"))))

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

;; this is ugly as fuck
(defn add-subsystem [xml name]
  (print-err (str "adding ss " name))
  (let [module-name (str "urn:jboss:domain:fnbox-" name ":1.0")
        zip-xml (zip/xml-zip xml)]
    (if (zfx/xml1-> zip-xml zf/descendants :subsystem [(zfx/attr= :xmlns module-name)])
      xml
      (reduce (fn [xml profile-name]
                (if-let [profile (zfx/xml1-> (zip/xml-zip xml) zf/descendants :profile [(zfx/attr= :name profile-name)])]
                  (zip/root (zip/append-child profile
                                              {:tag :subsystem :attrs {:module module-name}}))
                  xml))
              xml
              ["ha" "default" nil]))))

(defn add-subsystems [xml]
  (reduce add-subsystem xml (keys fnbox-modules)))

(defn set-welcome-root [xml]
  xml)

(defn unquote-cookie-path [xml]
  xml)

(defn add-xa-datasource [xml]
  xml)


(defn transform-config [file]
  (let [in-file (io/file jboss-dir file)
        xml (xml/parse-trim in-file)
        out-file (io/file (.getParentFile in-file) (str "fnbox/" (.getName in-file)))]
    (println (str "transforming " file))
    (io/make-parents out-file)
    (backup-current-config in-file)
    (io/copy (with-out-str
               (xml/emit
                (-> xml
                    (increase-deployment-timeout)
                    (add-extensions)
                    (add-subsystems)
                    (set-welcome-root)
                    (unquote-cookie-path)
                    (add-xa-datasource))
                :indent 4))
             out-file)))
