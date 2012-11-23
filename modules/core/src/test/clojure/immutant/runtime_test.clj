;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.runtime-test
  (:use immutant.runtime
        clojure.test
        immutant.test.helpers)
  (:require [immutant.registry :as registry]
            [clojure.java.io   :as io]
            [dynapath.util     :as dp]))

(def a-value (atom "ham"))

(defn add-url [url]
  (dp/add-classpath-url (clojure.lang.RT/baseLoader) url))

(use-fixtures :each
              (fn [f]
                (reset! a-value "ham")
                (f)))

(defn do-nothing [])

(defn update-a-value 
  ([]    (update-a-value "biscuit"))
  ([arg] (reset! a-value arg)))

(deftest require-and-invoke-should-call-the-given-function
  (require-and-invoke "immutant.runtime-test/update-a-value")
  (is (= "biscuit" @a-value)))

(deftest require-and-invoke-should-call-the-given-function-with-args
  (require-and-invoke "immutant.runtime-test/update-a-value" ["gravy"])
  (is (= "gravy" @a-value)))

(deftest initialize-without-an-init-fn-should-load-init-ns
  (add-url (io/resource "project-root/src/"))
  (initialize nil nil)
  (is (= "immutant.init" @a-value)))

(deftest initialize-with-an-init-fn-should-not-load-init-ns
  (add-url (io/resource "project-root/src/"))
  (initialize "immutant.runtime-test/do-nothing" nil)
  (is-not (= "immutant.init" @a-value)))

(deftest initialize-with-an-init-fn-should-call-the-init-fn
  (add-url (io/resource "project-root/src/"))
  (initialize "immutant.runtime-test/update-a-value" nil)
  (is (= "biscuit" @a-value)))

(deftest initialize-with-no-init-fn-and-no-init-ns-should-do-nothing
  (initialize nil nil)
  (is (= "ham" @a-value)))


