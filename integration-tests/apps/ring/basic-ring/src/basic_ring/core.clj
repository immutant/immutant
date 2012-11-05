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
   :body (pr-str body)})

(defn handler [request]
  (let [body {:a-value @a-value
              :clojure-version (clojure-version)
              :config (reg/fetch :config)
              :app :basic-ring
              :handler :handler}]
    (reset! a-value "not-default")
    (response body)))

(defn another-handler [request]
  (reset! a-value "another-handler")
  (response (assoc (read-string (:body (handler request))) :handler :another-handler)))

(defn resource-handler [request]
  (let [res (io/as-file (io/resource "a-resource"))]
    (response (.trim (slurp res)))))

(defn request-echo-handler [request]
  (response (assoc request :body "<body>"))) ;; body is a inputstream, chuck it for now

(defn java-class-handler [request]
  (response (SomeClass/hello)))


(defn dev-handler [request]
   (let [original-project (dev/current-project)]
     (dev/add-dependencies! '[clj-http "0.5.5"] '[org.clojure/data.json "0.1.2"])
     (use 'clj-http.client)
     (dev/add-dependencies! '[org.yaml/snakeyaml "1.5"] "extra")
     (use 'basic-ring.extra)
     (response  {:original (:dependencies original-project)
                 :with-data-reader (if (not= "1.3.0" (clojure-version))
                                     (read-string "#basic-ring/i \"something\""))
                 :added '[[clj-http "0.5.5"]
                          [org.clojure/data.json "0.1.2"]
                          [org.yaml/snakeyaml "1.5"]]
                 :final (:dependencies (dev/current-project))})))

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
               (handler r)))
  (web/start "/restarter"
             (fn [r]
               (web/start "/restarter"
                          (fn [r]
                            (-> (handler r)
                                :body
                                read-string
                                (assoc :restarted true)
                                response)))
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
