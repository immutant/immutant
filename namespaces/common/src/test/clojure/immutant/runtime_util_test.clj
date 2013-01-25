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

(ns immutant.runtime-util-test
  (:use immutant.runtime-util
        clojure.test
        midje.sweet
        [midje.util :only [expose-testables]])
  (:require [clojure.java.io             :as io]))

(deftest all-tests
  
  (let [app-root (io/file (io/resource "project-root"))]
    
    (fact "lib-dir should work"
      (lib-dir {:root app-root}) => (io/file app-root "lib"))
    
    (facts "bundled-jars"
      (let [jar-set (bundled-jars {:root app-root})]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["project-root/lib/some.jar"
                                          "project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "project-root/lib/some.txt"))))
        
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "project-root/lib/dev/invalid.jar"))))))


  (let [app-root (io/file (io/resource "non-project-root"))]

    (fact "lib-dir should work"
      (lib-dir {:root app-root}) => (io/file app-root "lib"))

    (facts "bundled-jars"
      (let [jar-set (bundled-jars {:root app-root})]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["non-project-root/lib/some.jar"
                                          "non-project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "non-project-root/lib/some.txt"))))
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "non-project-root/lib/dev/invalid.jar"))))))

  (tabular
   (fact "normalize-profiles"
     (normalize-profiles ?given) => ?expected)
   ?expected    ?given
   #{:default}  nil
   #{:default}  []
   #{:foo}      [:foo]
   #{:foo}      [":foo"]
   #{:foo}      ["foo"]
   #{:foo :bar} [:foo :bar]
   #{:foo :bar} [":foo" :bar]))


