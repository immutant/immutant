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

(ns immutant.runtime.test.bootstrap
  (:use immutant.runtime.bootstrap
        clojure.test
        midje.sweet
        immutant.test.helpers
        [midje.util :only [expose-testables]])
  (:require [clojure.java.io             :as io]
            [cemerick.pomegranate.aether :as aether]))

(deftest all-tests

  (expose-testables immutant.runtime.bootstrap)
  
  (let [app-root (io/file (io/resource "project-root"))
        another-app-root (io/file (io/resource "another-project-root"))]
    (fact "read-project should work"
      (:ham (read-project app-root)) => :biscuit)

    (fact "read-and-stringify-project should work"
      (read-and-stringify-project app-root) => (contains {"ham" :biscuit}))

    (facts "read-and-stringify-project should stringify an init fn"
      (get-in (read-and-stringify-project app-root) ["immutant" "init"]) => "some.namespace/init"
      (get-in (read-and-stringify-project another-app-root) ["immutant" "init"]) => "some.namespace/string")

    (fact "resource-paths should work"
      (let [paths (map #(.getAbsolutePath (io/file app-root %))
                       ["dev-resources" "resources" "native" "src" "classes" "target/classes"])]
        (resource-paths app-root) => (just paths :in-any-order)))
    
    (fact "lib-dir should work"
      (lib-dir app-root) => (io/file app-root "lib"))
    
    (facts "bundled-jars"
      (let [jar-set (bundled-jars (io/file app-root))]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["project-root/lib/some.jar"
                                          "project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "project-root/lib/some.txt"))))
        
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "project-root/lib/dev/invalid.jar")))))
       
    (facts "get-dependencies"
      (let [deps (get-dependencies app-root true)]

        (fact "should return deps from project.clj"
          deps => (contains (aether/dependency-files
                             (aether/resolve-dependencies
                              :coordinates [['org.clojure/clojure "1.3.0"]]))))

        (fact "should return deps from lib"
          deps => (contains (io/file (io/resource "project-root/lib/some.jar"))))

        (fact "should exclude lib deps from resolved deps"
          deps     => (contains (io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar")))
          deps =not=> (contains (filter #(= "tools.logging-0.2.3.jar" (.getName %))
                                        (aether/dependency-files
                                         (aether/resolve-dependencies
                                          :coordinates [['org.clojure/tools.logging "0.2.3"]])))))))

    (fact "get-dependencies without resolve-deps should only return jars from lib"
      (get-dependencies app-root false) => (just
                                            (io/file (io/resource "project-root/lib/some.jar"))
                                            (io/file (io/resource "project-root/lib/some-other.jar"))
                                            (io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar"))
                                            :in-any-order)))


  (let [app-root (io/file (io/resource "non-project-root"))]
    (fact "read-project should return nil"
      (read-project app-root) => nil?)

    (fact "read-and-stringify-project should return nil"
      (read-and-stringify-project app-root) => nil?)

    (fact "resource-paths should work"
      (resource-paths app-root) => (just (map #(.getAbsolutePath (io/file app-root %))
                                              ["resources" "native" "src" "classes"])
                                         :in-any-order))

    (fact "lib-dir should work"
      (lib-dir app-root) => (io/file app-root "lib"))


    (facts "bundled-jars"
      (let [jar-set (bundled-jars (io/file app-root))]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["non-project-root/lib/some.jar"
                                          "non-project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "non-project-root/lib/some.txt"))))
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "non-project-root/lib/dev/invalid.jar")))))
    
    (fact "get-dependencies should return deps from lib"
      (get-dependencies app-root true) =>
      (contains (io/file (io/resource "non-project-root/lib/some.jar"))))

    (fact "get-dependencies without resolve-deps should only return jars from lib"
      (get-dependencies app-root false) => (just
                                            (io/file (io/resource "non-project-root/lib/some.jar"))
                                            (io/file (io/resource "non-project-root/lib/some-other.jar"))
                                            (io/file (io/resource "non-project-root/lib/tools.logging-0.2.3.jar"))
                                            :in-any-order)))

  (facts "resolve-dependencies"
    (let [project (read-project (io/file (io/resource "project-root")))
          expected-deps (set (aether/dependency-files
                              (aether/resolve-dependencies
                               :coordinates (:dependencies project))))]

      (fact "should return the correct deps when there are no unresolvable deps"
        (resolve-dependencies project) => expected-deps)

      (let [project-with-bad-dep (update-in project [:dependencies]
                                            conj ['i-dont-exist "1.0.0"])]
        (fact "should return the correct deps when there is an unresolvable dep"
          (resolve-dependencies project-with-bad-dep) => expected-deps)
        
        (let [project-with-bad-deps (update-in project-with-bad-dep [:dependencies]
                                               conj ['i-also-dont-exist "1.0.0"])]
          (fact "should return the correct deps when there are multiple unresolvable deps"
            (resolve-dependencies project-with-bad-deps) => expected-deps))))))
