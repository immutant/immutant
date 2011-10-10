(ns fnbox.integs.basic
  (:use [fntest.core])
  (:use clojure.test)
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io]))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "apps/ring/basic-ring/"
                       :app-function "basic-ring.core/handler"
                       :context-path "/basic-ring"
                       }))

(deftest simple "it should work"
  (let [result (client/get "http://localhost:8080/basic-ring")]
    (is (.startsWith (result :body) "Hello From Clojure inside TorqueBox!"))))

