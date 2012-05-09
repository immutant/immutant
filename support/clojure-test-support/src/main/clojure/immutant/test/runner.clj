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

(ns immutant.test.runner
  (:require [clojure.java.io :as io])
  (:use [clojure.test]
        [clojure.tools.namespace :only (find-namespaces-in-dir)]))

(defn test-dir []
  (io/file (System/getProperty "user.dir") "src/test/clojure"))

(let [namespaces (find-namespaces-in-dir (test-dir))]
  (when-not (empty? namespaces)
    (apply require namespaces)
    (when-not *compile-files*
      (let [results (atom [])]
        (let [report-orig report]
          (binding [report (fn [x] (report-orig x)
                             (swap! results conj (:type x)))]
            (apply run-tests namespaces)))
        (shutdown-agents)
        ;;(println @results)
        (System/exit (if (empty? (filter #(re-find #"fail|error|incorrect" (name %)) @results)) 0 -1))))))

