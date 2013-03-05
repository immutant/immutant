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

(ns immutant.build.assembler
  (:require [clojure.java.io :as io])
  (:use immutant.build.tools)
  (:gen-class))

(defn assemble [assembly-dir slim?]
  (with-assembly-root assembly-dir
    (prepare)
    (println "Immutant..... " (:immutant (versions)))
    (println "JBoss AS..... " (:jboss (versions)))
    (println "Polyglot..... " (:polyglot (versions)))
    (when slim?
      (prep-for-slimming))
    (lay-down-jboss)
    (install-modules)
    (install-polyglot-modules)
    (backup-configs)
    (transform-configs)
    (create-standalone-xml)
    (create-standalone-ha-xml)
    (when slim?
      (slim-modules)
      (slim-fs))))

(defn -main [assembly-path & [type]]
  (println "Assembling" type "Immutant...")
  (assemble (io/file assembly-path) (= "slim" type))
  (shutdown-agents))
