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

(ns immutant.build.tools
  (:import [org.apache.commons.io FileUtils])
  (:require [clojure.java.io                :as io]
            [clojure.java.shell             :as shell]
            [clojure.string                 :as str]
            [clojure.zip                    :as zip]
            [clojure.contrib.zip-filter     :as zf]
            [clojure.contrib.zip-filter.xml :as zfx]
            [clojure.xml                    :as xml]
            [clojure.contrib.lazy-xml       :as lazy-xml]
            [org.satta.glob                 :as glob])
  (:use [clojure.pprint :only [pprint]]))

(defn unzip* [& sh-args]
  (let [result (apply shell/sh sh-args)]
    (if (not= (:exit result) 0)
      (println "ERROR: zip extraction failed:" (:err result)))
    (:out result)))

(defn unzip [zip-file dest-dir]
  (try
    (unzip* "unzip" "-q" "-d" (str dest-dir) zip-file)
    (catch java.io.IOException e
      (println "'unzip' not found, trying 'jar'...")
      (unzip* "jar" "xvf" zip-file :dir (doto (io/file dest-dir)
                                          (.mkdir))))))

(defn with-message [message & body]
  (print (str message "... "))
  (flush)
  (doall body)
  (println "DONE"))

(defn print-err [& message]
  (comment)
  (binding [*out* *err*]
    (apply println message)))

(defn xml-zip [path]
  (zip/xml-zip (xml/parse path)))

(defn extract-versions [pom-path version-paths]
  (into {} (map (fn [[k path]]
                  [k (apply zfx/xml1-> (cons (xml-zip pom-path) (conj path zfx/text)))])
                version-paths)))

(defn init [assembly-root]
  (def root-dir (-> assembly-root .getCanonicalFile .getParentFile .getParentFile))
  (def root-pom (io/file root-dir "pom.xml"))
  (def build-dir (io/file (.getCanonicalFile assembly-root) "target/stage"))
  (def immutant-dir (io/file build-dir "immutant"))
  (def jboss-dir (io/file immutant-dir "jboss"))

  (def version-paths {:immutant [:version]
                      :jboss [:properties :version.jbossas]
                      :polyglot [:properties :version.polyglot]})

  (def versions (extract-versions root-pom version-paths))

  (def m2-repo (if (System/getenv "M2_REPO")
                 (System/getenv "M2_REPO")
                 (str (System/getenv "HOME") "/.m2/repository")))

  (def jboss-zip-file
    (str m2-repo "/org/jboss/as/jboss-as-dist/" (:jboss versions) "/jboss-as-dist-" (:jboss versions) ".zip"))

  (def immutant-modules (reduce (fn [acc file]
                               (assoc acc
                                 (second (re-find #"immutant-(.*)-module" (.getName file)))
                                 file))
                             {}
                             (glob/glob (str (.getAbsolutePath root-dir) "/modules/*/target/*-module"))))
  (def polyglot-modules
    ["hasingleton"]))

(defn looking-at? [tag loc]
  (= tag (:tag (zip/node loc))))

(defn install-module [module-dir]
  (let [name (second (re-find #"immutant-(.*)-module" (.getName module-dir)))
        dest-dir (io/file jboss-dir (str "modules/org/immutant/" name "/main"))]
    (FileUtils/deleteQuietly dest-dir)
    (FileUtils/copyDirectory module-dir dest-dir)))

(defn install-polyglot-module [name]
  (let [version (:polyglot versions)
        artifact-path (str m2-repo "/org/projectodd/polyglot-" name "/" version "/polyglot-" name "-" version "-module.zip")
        artifact-dir (io/file (System/getProperty "java.io.tmpdir") (str "immutant-" name "-" version))
        dest-dir (io/file jboss-dir (str "modules/org/projectodd/polyglot/" name "/main"))]
    (try 
      (.mkdir artifact-dir)
      (unzip artifact-path artifact-dir)
      (FileUtils/deleteQuietly dest-dir)
      (FileUtils/copyDirectory artifact-dir dest-dir)
      (finally
       (FileUtils/deleteQuietly artifact-dir)))))

(defn increase-deployment-timeout [loc]
  (zip/edit loc assoc-in [:attrs :deployment-timeout] "1200"))

(defn add-extension [prefix loc name]
  (let [module-name (str prefix name)]
    (zip/append-child loc {:tag :extension :attrs {:module module-name}})))

(defn add-polyglot-extensions [loc]
  (reduce (partial add-extension "org.projectodd.polyglot.") loc polyglot-modules))

(defn add-extensions [loc]
  (add-polyglot-extensions
   (reduce (partial add-extension "org.immutant.") loc (keys immutant-modules))))

(defn add-subsystem [prefix loc name]
  (let [module-name (str "urn:jboss:domain:" prefix name ":1.0")]
    (zip/append-child loc {:tag :subsystem :attrs {:xmlns module-name}})))

(defn add-polyglot-subsystems [loc]
  (reduce (partial add-subsystem "polyglot-") loc polyglot-modules))

(defn add-subsystems [loc]
  (add-polyglot-subsystems
   (reduce (partial add-subsystem "immutant-") loc (keys immutant-modules))))

(defn fix-profile [loc]
  (let [name (get-in (zip/node loc) [:attrs :name])]
    (if (= "default" name)
      (zip/remove loc)
      (add-subsystems (if (= "full-ha" name)
                        (zip/edit loc assoc-in [:attrs :name] "default")
                        loc)))))

(defn fix-socket-binding-group [loc]
  (if (looking-at? :socket-binding-groups (zip/up loc))
    (let [name (get-in (zip/node loc) [:attrs :name])]
      (cond
       (= "standard-sockets" name) (zip/remove loc)
       (= "full-ha-sockets" name) (zip/edit loc assoc-in [:attrs :name] "standard-sockets")
       :else loc))
    loc))

(defn server-element [name offset]
  {:tag :server
   :attrs {:name name, :group "default"},
   :content [{:tag :jvm
              :attrs {:name "default"},
              :content [{:tag :heap
                         :attrs {:size "256m", :max-size "1024m"}}
                        {:tag :permgen
                         :attrs {:size "256m", :max-size "512m"}}]},
             {:tag :socket-bindings
              :attrs {:port-offset offset}}]})

(defn replace-servers [loc]
  (zip/replace loc {:tag :servers, :content [(server-element "server-01" "0")
                                             (server-element "server-02" "100")]}))

(defn replace-server-groups [loc]
  (zip/replace loc {:tag :server-groups
                    :content [{:tag :server-group
                               :attrs {:name "default", :profile "default"}
                               :content [{:tag :socket-binding-group
                                          :attrs {:ref "standard-sockets"}}]}]}))

(defn add-system-property [loc prop value]
  (zip/append-child loc {:tag :property :attrs {:name prop :value value}}))

(defn replace-system-property [loc prop value]
  (zip/edit loc assoc :attrs {:name prop :value value}))

(defn set-system-property [loc prop value]
  (if-let [child (zfx/xml1-> loc :property (zfx/attr= :name value))]
    (zip/up (replace-system-property child prop value))
    (add-system-property loc prop value)))

(defn unquote-cookie-path [loc]
  (set-system-property loc "org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR" "false"))

(defn set-welcome-root [loc]
  (if (= "false" (-> loc zip/node :attrs :enable-welcome-root))
    loc
    (zip/edit loc assoc-in [:attrs :enable-welcome-root] "false")))

(defn disable-security [loc]
  (zip/edit loc update-in [:attrs] dissoc :security-realm))

(defn add-logger-levels [loc]
  (zip/append-child (zip/right
                     (zip/insert-right loc
                                       {:tag :logger
                                        :attrs {:category "org.jboss.as.dependency.private"}}))
                    {:tag :level :attrs {:name "ERROR"}}))

(defn disable-hq-security [loc]
  (zip/append-child loc {:tag :security-enabled :content ["false"]}))

(defn disable-flow-control
  "Assumes loc is :jms-connection-factories and its first child is the in-vm one"
  [loc]
  (zip/append-child (zip/down loc) {:tag :consumer-window-size :content ["1"]}))

(defn append-system-properties [loc]
  (if (looking-at? :system-properties (zip/right loc))
    loc
    (zip/insert-right loc {:tag :system-properties})))

(defn fix-extensions [loc]
  (add-extensions (append-system-properties loc)))

(defn prepare-zip
  [file]
  (zip/xml-zip (xml/parse file)))

(defn walk-the-doc [loc]
  (if (zip/end? loc)
    (zip/root loc)
    (recur (zip/next
            (cond
             (looking-at? :extensions loc) (fix-extensions loc)
             (looking-at? :profile loc) (fix-profile loc)
             (looking-at? :periodic-rotating-file-handler loc) (add-logger-levels loc)
             (looking-at? :virtual-server loc) (set-welcome-root loc)
             (looking-at? :system-properties loc) (unquote-cookie-path loc)
             (looking-at? :jms-destinations loc) (zip/remove loc)
             (looking-at? :deployment-scanner loc) (increase-deployment-timeout loc)
             (looking-at? :native-interface loc) (disable-security loc)
             (looking-at? :http-interface loc) (disable-security loc)
             (looking-at? :max-size-bytes loc) (zip/edit loc assoc :content ["20971520"])
             (looking-at? :address-full-policy loc) (zip/edit loc assoc :content ["PAGE"])
             (looking-at? :hornetq-server loc) (disable-hq-security loc)
             (looking-at? :jms-connection-factories loc) (disable-flow-control loc)
             (looking-at? :servers loc) (replace-servers loc)
             (looking-at? :server-groups loc) (replace-server-groups loc)
             (looking-at? :socket-binding-group loc) (fix-socket-binding-group loc)
             :else loc)))))
  
(defn transform-config [file]
  (let [in-file (io/file jboss-dir file)
        out-file in-file]
    (if (re-find #"immutant" (slurp in-file))
      (println file "already transformed, skipping")
      (do
        (println "transforming" file)
        (io/make-parents out-file)
        (io/copy (with-out-str
                   (lazy-xml/emit
                    (walk-the-doc (prepare-zip in-file))
                    :indent 4))
                 out-file)))))
