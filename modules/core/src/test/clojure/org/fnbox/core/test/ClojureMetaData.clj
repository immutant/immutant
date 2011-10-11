;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

(ns org.fnbox.core.test.ClojureMetaData
  (:use clojure.test)
  (:import [org.fnbox.core ClojureMetaData])
  (:require [clojure.java.io :as io]))

(def simple-descriptor (io/file (io/resource "simple-descriptor.clj")))
(def hashy-descriptor (io/file (io/resource "hashy-descriptor.clj")))

(deftest parse "it should parse the descriptor and return a map"
  (let [result (ClojureMetaData/parse simple-descriptor)]
    (is (= "the-app-function" (.get result "app-function")))))

(deftest getString "it should return the proper value as a String"
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse simple-descriptor))
        value (.getString cmd "app-function")]
    (is (= "the-app-function" value))
    (is (instance? String value))))

(deftest getHash "it should return the proper value as a Hash"
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse hashy-descriptor))
        value (.getHash cmd "ham")]
    (is (= {:biscuit "gravy"} value))))

(deftest getAppFunction "it should return the proper value"
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse simple-descriptor))]
    (is (= "the-app-function" (.getAppFunction cmd)))))

(deftest it-should-allow-access-to-any-metadata-value
  (let [cmd (ClojureMetaData. "app-name" (ClojureMetaData/parse simple-descriptor))]
    (is (= "biscuit" (.getString cmd "ham")))))

