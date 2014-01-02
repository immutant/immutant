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

(ns dynapath-test.in-container
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [dynapath.util :as dp]
            [immutant.util :as util]
            [immutant.registry :as registry]))

(defn classloader []
  (-> (registry/get "clojure-runtime")
      .getClassLoader))

(deftest classpath-urls-should-work
  (is (some
       #(-> %
            .toString
            (.endsWith "dynapath-0.2.3.jar"))
       (dp/classpath-urls (classloader)))))

(deftest add-classpath-url-should-work
  (is (nil? (util/try-resolve 'progress.file/done?)))
  (dp/add-classpath-url
   (classloader)
   (io/resource "progress-1.0.1.jar"))
  (is (util/try-resolve 'progress.file/done?)))
