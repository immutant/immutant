(ns immutant.web-test
  (:require [clojure.test :refer :all]
            [immutant.web :refer :all]
            [testing.web  :refer [get-body hello handler]]
            [testing.hello.service :as pedestal]))

(def url "http://localhost:8080/")

(deftest run-should-var-quote
  (run hello)
  (is (= "hello" (get-body url)))
  (with-redefs [hello (handler "howdy")]
    (is (= "howdy" (get-body url)))))

(deftest mount-unquoted-handler
  (mount (server) hello)
  (is (= "hello") (get-body url))
  (with-redefs [hello (handler "hi")]
    (is (= "hello" (get-body url)))))

(deftest mount-quoted-handler
  (mount (server) #'hello)
  (is (= "hello") (get-body url))
  (with-redefs [hello (handler "hi")]
    (is (= "hi" (get-body url)))))

(deftest mount-pedestal-service
  (mount-servlet (server) pedestal/servlet)
  (is (= "Hello World!" (get-body url))))
