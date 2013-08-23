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

(ns org.immutant.web.ring.test.RingMetaData
  (:use clojure.test
        midje.sweet)
  (:import  org.immutant.bootstrap.ApplicationBootstrapUtils
            org.immutant.core.ClojureMetaData
            org.immutant.web.ring.RingMetaData)
  (:require [clojure.java.io :as io]))

;; init the global runtime
(ApplicationBootstrapUtils/preInit)

(defn make-md [data]
  (RingMetaData. (ClojureMetaData. "app-name" data)))

(defn parse-dd [name]
  (ClojureMetaData/parse (io/file (io/resource (str name ".clj")))))

(defn get-hosts [name]
  (-> name
      parse-dd 
      make-md
      .getHosts))

(deftest all-tests

  (facts "parsing the dd"
    (fact "multiple hosts should come through as a list"
      (get-hosts "multi-host-descriptor") => ["ham.biscuit", "gravy.biscuit"])
    
    (fact "a single host should come through as a list"
      (get-hosts "single-host-descriptor") => ["ham.biscuit"])
    
    (fact "a no host should come through as an empty list"
      (get-hosts "no-host-descriptor") => []))

  (tabular
   (fact "context-path normalization"
     (.getContextPath (make-md {"context-path" ?given})) => ?expected)
   ?expected ?given
   "/foo"    "/foo"
   "/foo"    "/foo/"
   "/foo"    "foo"
   "/"       "/"
   nil       nil
   "/"       ""))


