(ns org.fnbox.core.processors.t-AppCljParsingProcessor
  (:use clojure.test)
  (:use fnbox.test.as.helpers)
  (:import [org.fnbox.core.processors AppCljParsingProcessor])
  (:import [org.fnbox.core ClojureMetaData])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(AppCljParsingProcessor.)]))

(deftest it-should-raise-with-no-root-specified
  (is (thrown? RuntimeException
               (.deployResourceAs *harness* (io/resource "simple-descriptor.clj") "app.clj" ))))

(deftest it-should-raise-with-an-invalid-root-specified
  (is (thrown? RuntimeException
               (.deployResourceAs *harness* (io/resource "invalid-root-descriptor.clj") "app.clj" ))))

(deftest it-should-create-metadata-when-given-a-valid-root
  (let [unit (.deployResourceAs *harness* (io/resource "valid-root-descriptor.clj") "app.clj" )]
    (is (not (nil? (.getAttachment unit ClojureMetaData/ATTACHMENT_KEY))))))

(deftest it-should-populate-the-metadata
  (let [unit (.deployResourceAs *harness* (io/resource "valid-root-descriptor.clj") "app.clj" )
        metadata (.getAttachment unit ClojureMetaData/ATTACHMENT_KEY)]
    (are [exp val-method] (= exp (val-method metadata))
         "vfs:/tmp/"        .getRootPath
         "the-app-function" .getAppFunction
         "app"              .getApplicationName)
    (is (= "biscuit" (.getString metadata "ham")))))

