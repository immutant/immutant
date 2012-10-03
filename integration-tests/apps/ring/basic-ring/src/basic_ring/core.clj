(ns basic-ring.core
  (:require [immutant.messaging      :as msg]
            [immutant.web            :as web]
            [immutant.web.session    :as isession]
            [immutant.repl           :as repl]
            [immutant.registry       :as reg]
            [immutant.utilities      :as util]
            [immutant.dev            :as dev]
            [ring.middleware.session :as rsession]
            [clojure.java.io         :as io])
  (:import SomeClass))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body body})
(defn handler [request]
  (let [body (str "Hello from Immutant! This is basic-ring <p>a-value:" @a-value
                  "</p><p>version:" (clojure-version) "</p><p>"
                  (pr-str (reg/fetch :config)) "</p>")]
    (reset! a-value "not-default")
    (response body)))

(defn another-handler [request]
  (reset! a-value "another-handler")
  (handler request))

(defn resource-handler [request]
  (let [res (io/as-file (io/resource "a-resource"))]
    (response (slurp res))))

(defn request-echo-handler [request]
  (response (prn-str (assoc request :body "<body>")))) ;; body is a inputstream, chuck it for now

(defn java-class-handler [request]
  (response (SomeClass/hello)))


(defn dev-handler [request]
   (let [original-project (dev/current-project)]
     (dev/merge-dependencies! '[clj-rome "0.3.0"] '[org.clojure/data.json "0.1.2"])
     (use 'clj-rome.reader)
     (dev/merge-dependencies! '[org.yaml/snakeyaml "1.5"])
     (println (dev/reload-project!
               (update-in (dev/current-project) [:source-paths]
                          #(conj % (io/file (util/app-root) "extra")))))
     (use 'basic-ring.extra)
     (let [body (pr-str {:original (:dependencies original-project)
                         :added '[[clj-rome "0.3.0"]
                                  [org.clojure/data.json "0.1.2"]
                                  [org.yaml/snakeyaml "1.5"]]
                         :final (:dependencies (dev/current-project))})]
       (response body))))

(defn init []
  (println "INIT CALLED"))


(defn init-web []
  (init)
  (web/start handler))

(defn init-messaging []
  (init)
  (msg/start "/queue/ham")
  (msg/start "/queue/biscuit")
  (msg/listen "/queue/biscuit" #(msg/publish "/queue/ham" (.toUpperCase %))))

(defn init-web-start-testing []
  (init)
  (web/start "/starter"
             (fn [r]
               (web/start "/stopper"
                          (fn [r]
                            (web/stop "/stopper")
                            (web/stop "/starter")
                            (handler r)))
               (handler r))))

(defn init-resources []
  (init)
  (web/start resource-handler))

(defn init-request-echo []
  (init)
  (web/start request-echo-handler)
  (web/start "/foo/bar" request-echo-handler))

(defn init-nrepl []
  (init)
  (repl/start-nrepl 4321))

(defn init-java-class []
  (init)
  (web/start java-class-handler))


(defn init-dev-handler []
     (init)
     (web/start dev-handler))
