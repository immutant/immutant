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

(ns basic-ring.core
  (:require [immutant.messaging      :as msg]
            [immutant.web            :as web]
            [immutant.web.session    :as isession]
            [immutant.repl           :as repl]
            [immutant.registry       :as registry]
            [immutant.util           :as util]
            [immutant.dev            :as dev]
            [ring.middleware.session :as rsession]
            [clojure.java.io         :as io]
            [ham.biscuit             :as hb])
  (:import SomeClass))

(def a-value (atom "default"))

(println "basic-ring.core LOADED")

(defn response [body & [headers]]
  {:status 200
   :headers (merge {"Content-Type" "text/html"} headers)
   :body (pr-str body)})

(defn handler [request]
  (let [body {:a-value @a-value
              :clojure-version (clojure-version)
              :config (registry/get :config)
              :app :basic-ring
              :handler :handler
              :ham-biscuit-location hb/location
              :current-servlet-request? (not (nil? (web/current-servlet-request)))}]
    (when (> (:minor *clojure-version*) 3)
      (require '[cljs.closure :as cljs])
      (eval '(cljs/get-upstream-deps))) ;; verify clojurescript likes our classloader
    (reset! a-value "not-default")
    (response body)))

(defn another-handler [request]
  (reset! a-value "another-handler")
  (response (assoc (read-string (:body (handler request))) :handler :another-handler)))

(defn resource-handler [request]
  (let [res (io/as-file (io/resource "a-resource"))]
    (response (.trim (slurp res)))))

(defn request-echo-handler [request]
  (response (assoc request :body "<body>") ;; body is a inputstream, chuck it for now
            {"x-request-method" (str (:request-method request))})) 

(defn target-classes-handler [request]
  (response (SomeClass/hello)))

(defn esoteric-classes-handler [request]
  (response
   (reduce
    (fn [res class]
      (let [try-import
            #(try
               (eval `(import (quote ~%)))
               :success
               (catch Throwable _
                 :failure))]
        (-> res
            (update-in [(try-import class)] conj class)
            (update-in [:total] inc))))
    {:success [] :failure [] :total 0}
    ['com.sun.net.httpserver.spi.HttpServerProvider
     'com.sun.net.httpserver.Authenticator
     'javax.xml.ws.ProtocolException
     'javax.xml.bind.Binder
     'javax.xml.bind.annotation.DomHandler
     'javax.xml.bind.annotation.adapters.XmlAdapter
     'javax.xml.bind.attachment.AttachmentMarshaller
     'javax.xml.bind.helpers.AbstractMarshallerImpl
     'javax.xml.bind.util.JAXBResult
     'javax.xml.soap.Detail
     'javax.xml.ws.Dispatch
     'javax.xml.ws.handler.Handler
     'javax.xml.ws.handler.soap.SOAPHandler
     'javax.xml.ws.http.HTTPException
     'javax.xml.ws.soap.SOAPBinding
     'javax.xml.ws.spi.Invoker
     'javax.xml.ws.spi.http.HttpContext
     'javax.xml.ws.wsaddressing.W3CEndpointReference])))

(defn dev-handler [request]
   (let [original-project (dev/current-project)]
     (dev/add-dependencies! '[clj-http "0.5.5"] '[org.clojure/data.json "0.1.2"])
     (use 'clj-http.client)
     (dev/add-dependencies! '[org.yaml/snakeyaml "1.5"] "extra")
     (use 'basic-ring.extra)
     (response  {:original (:dependencies original-project)
                 :with-data-reader (read-string "#basic-ring/i \"something\"")
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
  (web/start target-classes-handler)
  (web/start "/import" esoteric-classes-handler))

(defn init-dev-handler []
     (init)
     (web/start dev-handler))
