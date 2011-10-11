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

(ns fnbox.integs
  (:use [clojure.test])
  (:use [fntest.core])
  (:use [clojure.tools.namespace :only (find-namespaces-in-dir)])
  (:require [clojure.java.io :as io]))

(let [integs (io/file (.getParentFile (io/file *file*)) "integs")
      namespaces (find-namespaces-in-dir integs)]
  (apply require namespaces)
  (when-not *compile-files*
    (let [results (atom [])]
      (let [report-orig report]
        (binding [fntest.jboss/home "../build/assembly/target/stage/fnbox/jboss"
                  fntest.jboss/descriptor-root "target/.descriptors"
                  report (fn [x] (report-orig x)
                           (swap! results conj (:type x)))]
          (with-jboss #(apply run-tests namespaces))))
      (shutdown-agents)
      (System/exit (if (empty? (filter #{:fail :error} @results)) 0 -1)))))

