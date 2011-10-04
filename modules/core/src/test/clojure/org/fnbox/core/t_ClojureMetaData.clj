(ns org.fnbox.core.t-ClojureMetaData
  (:use clojure.test)
  (:import [org.fnbox.core ClojureMetaData])
  (:require [clojure.java.io :as io]))

(def simple-descriptor (io/file (io/resource "simple-descriptor.clj")))

(deftest parse "it should parse the descriptor and return a map"
  (let [result (ClojureMetaData/parse simple-descriptor)]
    (is (= "the-app-function" (.get result "app-function")))))

(deftest getString "it should return the proper value as a String"
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse simple-descriptor))
        value (.getString cmd "app-function")]
    (is (= "the-app-function" value))
    (is (instance? String value))))

(deftest getAppFunction "it should return the proper value"
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse simple-descriptor))]
    (is (= "the-app-function" (.getAppFunction cmd)))))

