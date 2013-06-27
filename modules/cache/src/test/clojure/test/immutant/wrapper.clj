;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns test.immutant.wrapper
  (:use [immutant.cache]
        [clojure.test])
  (:require [immutant.codecs :as core]))

(defmethod core/encode :bs [data & args]
  (.getBytes (pr-str data)))

(defmethod core/decode :bs [data & args]
  (and data (read-string (String. data))))

(deftest storing-byte-arrays
  (let [c (create "bytes" :encoding :bs)]
    (is (nil? (put c :a 1)))
    (is (= 1 (get c :a)))))
