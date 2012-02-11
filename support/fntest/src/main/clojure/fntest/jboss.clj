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
  (:use [clojure.data.json :only (json-str read-json)])
  (:use [clojure.java.shell :only (sh)])
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io]))

(def ^:dynamic home (System/getenv "JBOSS_HOME"))
(def ^:dynamic descriptor-root ".descriptors")
(def api-url "http://localhost:9990/management")

(defn api
  "Params assembled into a hash that is passed to the JBoss CLI as a
   request and returns the JBoss response as a hash"
  [& params]
  (try
    (let [body (json-str (apply hash-map params))
          response (client/post api-url {:body body
                                         :headers {"Content-Type" "application/json"}
                                         :throw-exceptions false})]
      (read-json (response :body)))
    (catch Exception e)))

(defn ready?
  "Returns true if JBoss is ready for action"
  []
  (let [response (api :operation "read-attribute" :name "server-state")]
    (and response
         (= "success" (response :outcome))
         (= "running" (response :result)))))

(defn wait-for-ready?
  "Returns true if JBoss is up. Otherwise, sleeps for one second and
   then retries, effectively blocking the current thread until JBoss
   becomes ready or 'attempts' number of seconds has elapsed"
  [attempts]
  (or (ready?)
      (when (> attempts 0)
        (Thread/sleep 1000)
        (recur (dec attempts)))))

(defn start-command []
  (let [java-home (System/getProperty "java.home")
        jboss-home (.getCanonicalFile (io/file home))]
  (str java-home "/bin/java -Xms64m -Xmx1024m -XX:MaxPermSize=1024m -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSClassUnloadingEnabled -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dorg.jboss.boot.log.file=" jboss-home "/standalone/log/boot.log -Dlogging.configuration=file:" jboss-home "/standalone/configuration/logging.properties -jar " jboss-home "/jboss-modules.jar -mp " jboss-home "/modules -jaxpmodule javax.xml.jaxp-provider org.jboss.as.standalone -Djboss.home.dir=" jboss-home)))

(defn start
  "Start up a JBoss or toss exception if one is already running"
  []
  (if (ready?)
    (throw (Exception. "JBoss is already running!"))
    (let [cmd (start-command)]
      (println cmd)
      (future (apply sh (.split cmd " "))))))

(defn stop
  "Shut down whatever JBoss instance is responding to api-url"
  []
  (api :operation "shutdown"))

(defn descriptor
  "Return a File object representing the deployment descriptor"
  [name & [content]]
  (let [fname (if (re-seq #".+\.clj$" name) name (str name ".clj"))
        file (io/file descriptor-root fname)]
    (when content
      (io/make-parents file)
      (spit file (into content {:root (str (.getCanonicalFile (io/file (:root content))))})))
    file))

(defn deploy
  "Create an app deployment descriptor from the content and deploy it"
  [name content]
  (let [file (if (= java.io.File (class content)) content (descriptor name content))
        fname (.getName file)
        url (str (.toURL file))
        add (api :operation "add" :address ["deployment" fname] :content [{:url url}])]
    (when-not (= "success" (add :outcome))
      (api :operation "remove" :address ["deployment" fname])
      (api :operation "add" :address ["deployment" fname] :content [{:url url}]))
    (api :operation "deploy" :address ["deployment" fname])))

(defn undeploy
  "Undeploy the app named name"
  [name]
  (let [fname (.getName (descriptor name))]
    (api :operation "remove" :address ["deployment" fname])))

