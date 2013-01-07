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

(ns immutant.build.generate-sha1
  (:require [clojure.java.io :as io]
            [digest          :as digest])
  (:gen-class))

(defn generate-sha1
  ([filename]
     (generate-sha1 filename (str filename ".sha1")))
  ([filename checksum-filename]
     (spit (io/file checksum-filename) (digest/sha1 (io/file filename)))))

(defn -main [dirname & suffixes]
  (let [dir (io/file dirname)]
    (doall
     (for [f (file-seq dir)
           :when (and (= dir (.getParentFile f))
                      (some (fn [s] (.endsWith (.getName f) s)) suffixes))]
       (do
         (println "Generating sha1 of:" (.getName f))
         (generate-sha1 f))))))
