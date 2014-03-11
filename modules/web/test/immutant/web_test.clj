(ns immutant.web-test
  (:require [clojure.test :refer :all]
            [immutant.web :refer :all]
            [testing.web  :refer [get-body hello handler]]
            [testing.hello.service :as pedestal])
  (import clojure.lang.ExceptionInfo))

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

(deftest unmount-yields-404
  (run hello)
  (is (= "hello" (get-body url)))
  (unmount)
  (let [result (is (thrown? ExceptionInfo (get-body url)))]
    (is (= 404 (-> result ex-data :object :status)))))

(deftest mount-should-call-init
  (let [p (promise)]
    (run hello :init #(deliver p 'init))
    (is (= 'init @p))))

(deftest unmount-should-call-destroy
  (let [p (promise)]
    (run hello :destroy #(deliver p 'destroy))
    (is (not (realized? p)))
    (unmount)
    (is (= 'destroy @p))))
