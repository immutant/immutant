(ns fnbox.integs.basic
  (:use [fntest.core])
  (:use clojure.test)
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io]))

(def deployment
  (with-deployment "basic"
    {
     :root (str (.getCanonicalFile (io/file "apps/ring/basic-ring/")))
     :app-function "basic-ring.core/handler"
     :context-path "/basic-ring"
     }))

(use-fixtures :once with-jboss deployment)

(deftest simple "it should work"
  (let [result (client/get "http://localhost:8080/basic-ring")]
    (is (.startsWith (result :body) "Hello From Clojure inside TorqueBox!"))))

