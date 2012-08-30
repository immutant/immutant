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

(ns fntest.jboss
  (:require [clojure.java.shell  :as eval]
            [clojure.java.io     :as io]
            [jboss-as.management :as api]))

(def ^:dynamic *home* (System/getenv "JBOSS_HOME"))
(def ^:dynamic *descriptor-root* ".descriptors")

(defn start-command []
  (let [java-home (System/getProperty "java.home")
        jboss-home (.getCanonicalFile (io/file *home*))]
    (str java-home
         "/bin/java -Xms64m -Xmx1024m -XX:MaxPermSize=1024m -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSClassUnloadingEnabled"
         (if (= "true" (System/getProperty "fntest.debug"))
           " -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y"
           "")
         " -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dorg.jboss.boot.log.file=" jboss-home "/standalone/log/boot.log -Dlogging.configuration=file:" jboss-home "/standalone/configuration/logging.properties -jar " jboss-home "/jboss-modules.jar -mp " jboss-home "/modules -jaxpmodule javax.xml.jaxp-provider org.jboss.as.standalone -Djboss.home.dir=" jboss-home)))

(defn wait-for-ready?
  "Returns true if JBoss is up. Otherwise, sleeps for one second and
   then retries, effectively blocking the current thread until JBoss
   becomes ready or 'attempts' number of seconds has elapsed"
  [attempts]
  (or (api/ready?)
      (when (> attempts 0)
        (Thread/sleep 1000)
        (recur (dec attempts)))))

(defn start
  "Start up a JBoss or toss exception if one is already running"
  []
  (if (api/ready?)
    (throw (Exception. "JBoss is already running!"))
    (let [cmd (start-command)]
      (println cmd)
      (future (apply eval/sh (.split cmd " "))))))

(defn stop
  "Shut down whatever JBoss instance is responding to api-url"
  []
  (api/shutdown))

(defn descriptor
  "Return a File object representing the deployment descriptor"
  [name & [content]]
  (let [fname (if (re-seq #".+\.clj$" name) name (str name ".clj"))
        file (io/file *descriptor-root* fname)]
    (when content
      (io/make-parents file)
      (spit file (into content {:root (str (.getCanonicalFile (io/file (:root content))))})))
    file))

(defn deployment-name
  "Determine the name for the deployment."
  [name]
  (if (.endsWith name ".ima")
    name
    (.getName (descriptor name))))

(defn deploy
  "Create an app deployment descriptor from the content and deploy it"
  ([content-map]
     (doseq [[name content] content-map]
       (deploy name content)))
  ([name content]
     (let [file (if (instance? java.io.File content) content (descriptor name content))
           fname (.getName file)
           url (.toURL file)
           add (api/add fname url)]
       (when-not (= "success" (add :outcome))
         (api/remove fname)
         (api/add fname url))
       (api/deploy fname))))

(defn undeploy
  "Undeploy the apps deployed under the given names"
  [& names]
  (doseq [name names]
    (api/remove (deployment-name name))))
