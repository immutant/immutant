;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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
            [clojure.set                    :as set]
            [clojure.zip                    :as zip]
            [clojure.data.zip.xml           :as zfx]
            [clojure.xml                    :as xml]
            [clojure.contrib.lazy-xml       :as lazy-xml]
            [org.satta.glob                 :as glob])
  (:use [clojure.pprint :only [pprint]]))

(def polyglot-modules
    ["hasingleton" "cache" "core" "web" "jobs" "messaging" "xa"])

(def polyglot-extensions
  ["hasingleton" "cache"])

(def fat-modules
  ["cmp" "ejb3" "jacorb" "jaxr" "jaxrs" "jdr" "jpa" "jsf" "jsr77" "mail"
   "sar" "webservices" "weld"])

(defn looking-at? [tag loc]
  (= tag (:tag (zip/node loc))))

(def extensions-to-remove (atom ["pojo"]))
(def subsystems-to-remove (atom ["pojo"]))
(def tags-to-remove (atom [(partial looking-at? :jms-destinations)]))

(def system-properties
  {"org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR" "false"
   "org.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH"     "true"
   "org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH"         "true"})

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

(defn extract-module-name [dir]
  (second (re-find #"immutant-(.*)-module-module" (.getName dir))))

(def version-paths {:immutant [:version]
                    :polyglot [:properties :version.polyglot]})

(def m2-repo (if (System/getenv "M2_REPO")
               (System/getenv "M2_REPO")
               (str (System/getenv "HOME") "/.m2/repository")))

(def ^:dynamic assembly-dir nil)
(def root-dir (memoize #(-> assembly-dir .getParentFile .getParentFile)))
(def build-dir (memoize #(io/file assembly-dir "target/stage")))
(def immutant-dir (memoize #(io/file (build-dir) "immutant")))
(def jboss-dir (memoize #(io/file (immutant-dir) "jboss")))
(def versions
  (memoize
    #(assoc (extract-versions (io/file (root-dir) "pom.xml") version-paths)
       :jboss (System/getProperty "version.jbossas"))))

(defn jboss-zip-file []
  (str m2-repo "/org/jboss/as/jboss-as-dist/"
       (:jboss (versions)) "/jboss-as-dist-"
       (:jboss (versions)) ".zip"))

(defn subsystem-module? [f]
  (->> f
       .getParentFile
       .getParentFile
       file-seq
       (filter #(= "Namespace.java" (.getName %)))
       seq
       nil?
       not))

(defn immutant-modules []
  (reduce (fn [acc file]
            (assoc acc
              (extract-module-name file)
              {:file       file
               :subsystem? (subsystem-module? file)}))
          {}
          (glob/glob (str (.getAbsolutePath (root-dir))
                          "/modules/*/target/*-module"))))

(defn immutant-subsystems []
  (->> (immutant-modules)
       (filter (comp :subsystem? second))
       (map first)))

(defmacro with-assembly-root [assembly-root & body]
  `(binding [assembly-dir  (-> ~assembly-root .getCanonicalFile)]
     ~@body))

(defn attr= [attr val loc]
  (= val (get-in (zip/node loc) [:attrs attr])))

(def name= (partial attr= :name))

(defn delete-module [path]
  (doseq [slot (.listFiles (io/file (jboss-dir) "modules" path))]
    (if (.exists (io/file slot "module.xml"))
      (FileUtils/deleteDirectory slot))))

(defn install-module [module-dir]
  (let [name (extract-module-name module-dir)
        dest-dir (io/file (jboss-dir) (str "modules/org/immutant/" name "/main"))]
    (FileUtils/deleteQuietly dest-dir)
    (FileUtils/copyDirectory module-dir dest-dir)))

(defn install-polyglot-module [name]
  (let [version (:polyglot (versions))
        artifact-path (str m2-repo "/org/projectodd/polyglot-" name "/" version "/polyglot-" name "-" version "-module.zip")
        artifact-dir (io/file (System/getProperty "java.io.tmpdir") (str "immutant-" name "-" version))
        dest-dir (io/file (jboss-dir) (str "modules/org/projectodd/polyglot/" name "/main"))]
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
  (reduce (partial add-extension "org.projectodd.polyglot.") loc polyglot-extensions))

(defn add-extensions [loc]
  (add-polyglot-extensions
   (reduce (partial add-extension "org.immutant.") loc (immutant-subsystems))))

(defn add-subsystem [prefix loc name]
  (let [module-name (str "urn:jboss:domain:" prefix name ":1.0")]
    (zip/append-child loc {:tag :subsystem :attrs {:xmlns module-name}})))

(defn add-polyglot-subsystems [loc]
  (reduce (partial add-subsystem "polyglot-") loc polyglot-extensions))

(defn add-subsystems [loc]
  (add-polyglot-subsystems
   (reduce (partial add-subsystem "immutant-") loc (immutant-subsystems))))

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

(defn set-system-properties [loc]
  (reduce (fn [loc [k v]]
            (set-system-property loc k v))
          loc
          system-properties))

(defn set-welcome-root [loc]
  (if (= "false" (-> loc zip/node :attrs :enable-welcome-root))
    loc
    (zip/edit loc assoc-in [:attrs :enable-welcome-root] "false")))

(defn disable-security [loc]
  (zip/edit loc update-in [:attrs] dissoc :security-realm))

(defn logger [category level]
  {:tag :logger
   :attrs {:category category}
   :content [{:tag :level
              :attrs {:name level}}]})

(defn add-logger-levels [loc]
  (-> loc
      (zip/insert-right (logger "org.jboss.as.dependency.private" "ERROR"))
      (zip/insert-right (logger "immutant.web" "DEBUG"))
      (zip/insert-right (logger "immutant.cache" "DEBUG"))))

(defn disable-hq-security [loc]
  (zip/append-child loc {:tag :security-enabled :content ["false"]}))

(defn enable-hq-jmx [loc]
  (zip/append-child loc {:tag :jmx-management-enabled :content ["true"]}))

(defn update-hq-server [loc]
  (-> loc
      disable-hq-security
      enable-hq-jmx))

(defn update-hq-invmcf
  "Assumes loc is :connection-factory"
  [loc]
  (if (= "InVmConnectionFactory" (-> loc zip/node :attrs :name))
    (-> loc
        (zip/append-child {:tag :consumer-window-size :content ["1"]})
        (zip/append-child {:tag :connection-ttl :content ["1800000"]}))
    loc))

(defn append-system-properties [loc]
  (if (looking-at? :system-properties (zip/right loc))
    loc
    (zip/insert-right loc {:tag :system-properties})))

(defn fix-extensions [loc]
  (add-extensions (append-system-properties loc)))

(defn remove-extensions [loc]
  (let [module (-> loc zip/node :attrs :module)]
    (if (some #(= (str "org.jboss.as." %) module) @extensions-to-remove)
      (zip/remove loc)
      loc)))

(defn remove-subsystems [loc]
  (let [xmlns (-> loc zip/node :attrs :xmlns)]
    (if (some #(re-find (re-pattern (str \: % \:)) xmlns) @subsystems-to-remove)
      (zip/remove loc)
      loc)))

(defn fix-socket-binding [loc]
  (if (= "http" (-> loc zip/node :attrs :name))
    (zip/edit loc assoc-in [:attrs :port] "${http.port:8080}")
    loc))

(defn polyglot-cache-container []
  {:tag :cache-container
   :attrs {:name "polyglot", :default-cache "default"},
   :content [{:tag :local-cache
              :attrs {:name "default", :start "EAGER"},
              :content [{:tag :eviction
                         :attrs {:strategy "LRU", :max-entries "10000"}}
                        {:tag :expiration
                         :attrs {:max-idle "100000"}}
                        {:tag :transaction
                         :attrs {:mode "FULL_XA"}}]}]})

(defn fix-cache-container [loc]
  (if (= "web" (-> loc zip/node :attrs :name))
    (if (looking-at? :transport (zip/down loc))
      (zip/edit loc update-in [:attrs :aliases] #(str "polyglot " %))
      (zip/insert-right loc (polyglot-cache-container)))
    loc))

(defn insert-jgroups-config [loc]
  (-> loc
      (zip/insert-child {:tag :jgroups-channel :content ["${msg.jgroups.channel:hq-cluster}"]})
      (zip/insert-child {:tag :jgroups-stack :content ["${msg.jgroups.stack:udp}"]})))

(defn replace-socket-with-jgroups [loc]
  (if-let [socket-binding (zfx/xml1-> loc :socket-binding)]
    (insert-jgroups-config (zip/remove socket-binding))
    loc))

(defn extract-extensions [path]
  (for [x (xml-seq (xml/parse (io/file (jboss-dir) path)))
        :when (= :extension (:tag x))]
    (get-in x [:attrs :module])))

(defn prepare-zip
  [file]
  (zip/xml-zip (xml/parse file)))

(defn remove-loc? [loc]
  (some #(% loc) @tags-to-remove))

(defn walk-the-doc [loc]
  (if (zip/end? loc)
    (zip/root loc)
    (recur (zip/next
            (if (remove-loc? loc)
              (zip/remove loc)
              (condp looking-at? loc
                :extensions                     (fix-extensions loc)
                :extension                      (remove-extensions loc)
                :subsystem                      (remove-subsystems loc)
                :profile                        (fix-profile loc)
                :periodic-rotating-file-handler (add-logger-levels loc)
                :virtual-server                 (set-welcome-root loc)
                :system-properties              (set-system-properties loc)
                :deployment-scanner             (increase-deployment-timeout loc)
                :native-interface               (disable-security loc)
                :http-interface                 (disable-security loc)
                :max-size-bytes                 (zip/edit loc assoc :content ["20971520"])
                :address-full-policy            (zip/edit loc assoc :content ["PAGE"])
                :hornetq-server                 (update-hq-server loc)
                :connection-factory             (update-hq-invmcf loc)
                :servers                        (replace-servers loc)
                :server-groups                  (replace-server-groups loc)
                :socket-binding-group           (fix-socket-binding-group loc)
                :socket-binding                 (fix-socket-binding loc)
                :cache-container                (fix-cache-container loc)
                :broadcast-group                (replace-socket-with-jgroups loc)
                :discovery-group                (replace-socket-with-jgroups loc)
                loc))))))
  
(defn transform-config [file]
  (let [in-file (io/file (jboss-dir) file)
        out-file in-file]
    (try
      (if (re-find #"immutant" (slurp in-file))
        (println file "already transformed, skipping")
        (do
          (with-message (str "Transforming " file)
            (io/make-parents out-file)
            (io/copy (with-out-str
                       (lazy-xml/emit
                        (walk-the-doc (prepare-zip in-file))
                        :indent 4))
                     out-file))))
      (catch java.io.FileNotFoundException e (println (.getMessage e))))))

(defn extract-module-deps [f]
  (let [mods (flatten
              (for [x (xml-seq (xml/parse f))
                    :when (and (or (= :module-alias (:tag x))
                                   (= :module (:tag x)))
                               (not= "true" (get-in x [:attrs :optional])))]
                [(get-in x [:attrs :name]) (get-in x [:attrs :target-name])]))]
    {(first mods) (filter identity (rest mods))}))

(defn extract-all-module-deps [jboss-dir]
  (apply merge
         (for [f (file-seq (io/file jboss-dir "modules"))
               :when (= "module.xml" (.getName f))]
           (extract-module-deps f))))

(defn find-full-required-module-set [top-level all]
  (loop [hk {:processed #{} :required (vec top-level)} pos 0]
    (let [mod (and (< pos (count (:required hk)))
                   (nth (:required hk) pos))
          hk (if (or (not mod)
                     (some #{mod}
                           (:processed hk)))
               hk
               (-> hk
                   (update-in [:processed] conj mod)
                   (update-in [:required] #(vec (concat % (all mod))))))]
      (if (not mod)
        (-> hk :required set)
        (recur hk (inc pos))))))

(defn prepare []
  (with-message (str "Creating " (immutant-dir))
    (io/make-parents (immutant-dir))))

(defn lay-down-jboss []
  (when-not (.exists (jboss-dir))
    (with-message "Laying down jboss"
      (unzip (jboss-zip-file) (immutant-dir)))
    (let [unzipped (str "jboss-as-" (:jboss (versions)))]
      (with-message (str "Moving " unzipped " to jboss")
        (.renameTo (io/file (immutant-dir) unzipped) (jboss-dir))))))

(defn install-modules []
  (with-message "Installing modules"
    (doseq [mod (map :file (vals (immutant-modules)))]
      (install-module mod))))

(defn install-polyglot-modules []
  (with-message "Installing polyglot modules"
    (doseq [mod polyglot-modules]
      (install-polyglot-module mod))))

(defn backup-configs []
  (doseq [cfg (map (partial io/file (jboss-dir))
                   ["standalone/configuration/standalone-ha.xml"
                    "standalone/configuration/standalone.xml"
                    "domain/configuration/domain.xml"])]
    (let [backup (io/file (.getParentFile cfg) (str "original-" (.getName cfg)))]
      (if-not (.exists backup)
        (io/copy cfg backup)))))

(defn transform-configs []
  (doseq [cfg ["standalone/configuration/standalone-full.xml"
               "standalone/configuration/standalone-full-ha.xml"
               "domain/configuration/domain.xml"
               "domain/configuration/host.xml"]]
    (transform-config cfg)))

(defn create-standalone-xml []
  (io/copy (io/file (jboss-dir) "standalone/configuration/standalone-full.xml")
           (io/file (jboss-dir) "standalone/configuration/standalone.xml")))

(defn create-standalone-ha-xml []
  (io/copy (io/file (jboss-dir) "standalone/configuration/standalone-full-ha.xml")
           (io/file (jboss-dir) "standalone/configuration/standalone-ha.xml")))

(defn prep-for-slimming []
  (apply swap! extensions-to-remove conj fat-modules)
  (apply swap! subsystems-to-remove conj fat-modules)
  
  (swap! tags-to-remove conj
         (partial looking-at? :pooled-connection-factory)
         #(and (looking-at? :socket-binding %)
               (name= "jacorb" %))
         #(and (looking-at? :socket-binding %)
               (name= "jacorb-ssl" %))
         #(and (looking-at? :security-domain %)
               (name= "jboss-ejb-policy" %))
         #(and (looking-at? :cache-container %)
               (name= "hibernate" %))
         #(and (looking-at? :datasource %)
               (attr= :jndi-name "java:jboss/datasources/ExampleDS" %))
         #(and (looking-at? :logger %)
               (attr= :category "jacorb" %))
         #(and (looking-at? :logger %)
               (attr= :category "jacorb.config" %))))

(defn extract-extensions-from-standalone-xml []
  (reduce
   (fn [acc ext]
     (if-let [exts (seq (extract-extensions ext))]
       (set (concat acc exts))
       acc))
   #{}
   ["standalone/configuration/standalone.xml"
    "standalone/configuration/standalone-ha.xml"
    "domain/configuration/domain.xml"]))

(defn slim-modules []
  (let [all-modules (extract-all-module-deps (jboss-dir))
        required-modules (-> (extract-extensions-from-standalone-xml)
                             (conj 
                              ;; add a few modules that aren't mentioned in any config,
                              ;; but are required at runtime
                              "org.jboss.as.host-controller"
                              "org.jboss.as.standalone"
                              "org.jboss.as.domain-http-error-context"
                              "com.h2database.h2"
                              "org.jboss.as.cli"
                              "org.fusesource.jansi"
                              "org.jboss.aesh"
                              "javaee.api"
                              "javax.servlet.jstl.api")
                             (find-full-required-module-set all-modules))]
    (doseq [m (filter identity (set/difference (set (keys all-modules)) required-modules))]
      (with-message (str "Deleting module " m)
        (delete-module (str "system/layers/base/" (.replace m "." "/")))))))

(defn slim-fs []
  (doseq [path ["appclient"
                "bin/appclient.bat" "bin/appclient.conf.bat"
                "bin/appclient.conf" "bin/appclient.sh" "bin/client"
                "bin/jconsole.bat" "bin/jconsole.sh" "bin/jdr.bat" "bin/jdr.sh"
                "bin/wsconsume.bat" "bin/wsconsome.sh" "bin/wsprovide.bat" "bin/wsprovide.sh"
                "bundles" "docs/examples" "docs/schema" "welcome-content"]]
    (let [f (io/file (jboss-dir) path)]
      (with-message (str"Deleting " path)
        (if (.isDirectory f)
          (FileUtils/deleteDirectory f)
          (.delete f)))))

  ;; delete empty module dirs
  (doseq [d (file-seq (io/file (jboss-dir) "modules"))]
    (and (.isDirectory d)
         (not (seq (.listFiles d)))
         (FileUtils/deleteDirectory d))))

(defn copy-static-config [slim?]
  (let [type (if slim? "slim" "full")]
    (with-message "Copying config files"
      (io/copy (io/file assembly-dir (format "src/resources/standalone.%s.xml" type))
        (io/file (jboss-dir) "standalone/configuration/standalone.xml"))
      (io/copy (io/file assembly-dir (format "src/resources/logging-standalone.properties" type))
               (io/file (jboss-dir) "standalone/configuration/logging.properties"))
      (io/copy (io/file assembly-dir (format "src/resources/standalone-ha.%s.xml" type))
               (io/file (jboss-dir) "standalone/configuration/standalone-ha.xml"))
      (io/copy (io/file assembly-dir (format "src/resources/domain.%s.xml" type))
               (io/file (jboss-dir) "domain/configuration/domain.xml"))
      (io/copy (io/file assembly-dir (format "src/resources/host.%s.xml" type))
               (io/file (jboss-dir) "domain/configuration/host.xml")))))
