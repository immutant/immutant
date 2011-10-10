(ns fntest.jboss
  (:use [clojure.data.json :only (json-str read-json)])
  (:use [clojure.java.shell :only (sh)])
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io]))

(def ^:dynamic home (System/getenv "JBOSS_HOME"))
(def ^:dynamic descriptor-root ".descriptors")
(def api-url "http://localhost:9990/management")

(defn api [& params]
  "Params assembled into a hash that is passed to the JBoss CLI as a
   request and returns the JBoss response as a hash"
  (try
    (let [body (json-str (apply hash-map params))
          response (client/post api-url {:body body :throw-exceptions false})]
      (read-json (response :body)))
    (catch Exception e)))

(defn ready? []
  "Returns true if JBoss is ready for action"
  (let [response (api :operation "read-attribute" :name "server-state")]
    (and response
         (= "success" (response :outcome))
         (= "running" (response :result)))))

(defn wait-for-ready? [attempts]
  "Returns true if JBoss is up. Otherwise, sleeps for one second and
   then retries, effectively blocking the current thread until JBoss
   becomes ready or 'attempts' number of seconds has elapsed"
  (or (ready?)
      (when (> attempts 0)
        (Thread/sleep 1000)
        (recur (dec attempts)))))

(defn start []
  "Start up a JBoss or toss exception if one is already running"
  (if (ready?)
    (throw (Exception. "JBoss is already running!"))
    (future (sh (str home "/bin/standalone.sh")))))

(defn stop []
  "Shut down whatever JBoss instance is responding to api-url"
  (api :operation "shutdown"))

(defn descriptor [name & [content]]
  "Return a File object representing the deployment descriptor"
  (let [fname (if (re-seq #".+\.clj$" name) name (str name ".clj"))
        file (io/file descriptor-root fname)]
    (when content
      (io/make-parents file)
      (spit file (into content {:root (str (.getCanonicalFile (io/file (:root content))))})))
    file))

(defn deploy [name content]
  "Create an app deployment descriptor from the content and deploy it"
  (let [file (descriptor name content)
        fname (.getName file)
        url (str (.toURL file))
        add (api :operation "add" :address ["deployment" fname] :content [{:url url}])]
    (when-not (= "success" (add :outcome))
      (api :operation "remove" :address ["deployment" fname])
      (api :operation "add" :address ["deployment" fname] :content [{:url url}]))
    (api :operation "deploy" :address ["deployment" fname])))

(defn undeploy [name]
  "Undeploy the app named name"
  (let [fname (.getName (descriptor name))]
    (api :operation "remove" :address ["deployment" fname])))

