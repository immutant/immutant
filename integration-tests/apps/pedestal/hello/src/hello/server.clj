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

; Copyright 2013 Relevance, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns hello.server
  (:gen-class) ; for -main method in uberjar
  (:require [hello.service :as service]
            [io.pedestal.service.http :as bootstrap]))

(def service-instance
  "Global var to hold service instance."
  nil)

(defn create-server
  "Standalone dev/prod mode."
  [& [opts]]
  (alter-var-root #'service-instance
                  (constantly (bootstrap/create-server (merge service/service opts)))))

(defn -main [& args]
  (create-server)
  (bootstrap/start service-instance))


;; Container prod mode for use with the io.pedestal.servlet.ClojureVarServlet class.

(defn servlet-init [this config]
  (alter-var-root #'service-instance
                  (constantly (bootstrap/create-servlet service/service)))
  (.init (::bootstrap/servlet service-instance) config))

(defn servlet-destroy [this]
  (alter-var-root #'service-instance nil))

(defn servlet-service [this servlet-req servlet-resp]
  (.service ^javax.servlet.Servlet (::bootstrap/servlet service-instance)
            servlet-req servlet-resp))
