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

(ns fnbox.build.assembler
  (:require [clojure.java.io :as io])
  (:use [fnbox.build.tools])
  (:gen-class))

(defn prepare []
  (with-message (str "Creating " fnbox-dir)
    (io/make-parents fnbox-dir)))

(defn lay-down-jboss []
  (when-not (.exists jboss-dir)
    (with-message "Laying down jboss"
      (unzip jboss-zip-file fnbox-dir))
    (let [unzipped (str "jboss-as-" (:jboss versions))]
      (with-message (str "Moving " unzipped " to jboss")
        (.renameTo (io/file fnbox-dir unzipped) jboss-dir)))))

(defn install-modules []
  (with-message "Installing modules"
    (doall (map install-module (vals fnbox-modules)))))

(defn transform-configs []
  (doall (map transform-config
              ["standalone/configuration/standalone.xml"
               "standalone/configuration/standalone-ha.xml"
               "domain/configuration/domain.xml"])))

(defn create-standalone-xml []
  (io/copy (io/file jboss-dir "standalone/configuration/fnbox/standalone.xml")
           (io/file jboss-dir "standalone/configuration/standalone.xml")))

(defn assemble [assembly-dir]
  (init assembly-dir)
  (prepare)
  (println (str "(fn box)..... " (:fnbox versions)))
  (println (str "JBoss AS..... " (:jboss versions)))
  (lay-down-jboss)
  (install-modules)
  (transform-configs)
  (create-standalone-xml))

(defn -main [assembly-path]
  (println "Assembling (fn box)...")
  (assemble (io/file assembly-path)))
