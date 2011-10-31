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

(ns immutant.test.runtime
  (:use immutant.runtime)
  (:use clojure.test)
  (:use immutant.test.helpers))

(def a-value (atom "ham"))

(defn update-a-value 
  ([]    (update-a-value "biscuit"))
  ([arg] (reset! a-value arg)))

(deftest require-and-invoke-should-call-the-given-function
  (require-and-invoke "clojure.core/println")
  (require-and-invoke "immutant.test.runtime/update-a-value")
  (is (= "biscuit" @a-value)))

(deftest require-and-invoke-should-call-the-given-function-with-args
  (require-and-invoke "immutant.test.runtime/update-a-value" ["gravy"])
  (is (= "gravy" @a-value)))
