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
      (:ham (read-project app-root nil)) => :biscuit)

    (fact "read-project with profiles should work"
      (:ham (read-project app-root [:gravy])) => :not-bacon)

    (fact "read-project without profiles should use the ones defined in :immutant"
      (:egg (read-project app-root nil)) => :biscuit)

    (fact "read-project with profiles should ignore the ones defined in :immutant"
      (:egg (read-project app-root [:gravy])) => :sandwich)

    (fact "read-and-stringify-full-app-config should work"
      (read-and-stringify-full-app-config nil app-root) => (contains {"ham" "basket"}))

    (facts "read-and-stringify-full-app-config should stringify an init fn"
      ((read-and-stringify-full-app-config nil app-root) "init") => "some.namespace/init"
      ((read-and-stringify-full-app-config nil another-app-root) "init") => "some.namespace/string")

    (fact "resource-paths should work"
      (let [paths (map #(.getAbsolutePath (io/file app-root %))
                       ["resources" "target/native" "src" "classes" "target/classes"])]
        (resource-paths app-root nil) => (just paths :in-any-order)))
    
    (fact "lib-dir should work"
      (lib-dir app-root nil) => (io/file app-root "lib"))
    
    (facts "bundled-jars"
      (let [jar-set (bundled-jars (io/file app-root) nil)]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["project-root/lib/some.jar"
                                          "project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "project-root/lib/some.txt"))))
        
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "project-root/lib/dev/invalid.jar")))))
       
    (facts "get-dependencies"
      (let [deps (get-dependencies app-root true nil)]

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
      (get-dependencies app-root false nil) => (just
                                                (io/file (io/resource "project-root/lib/some.jar"))
                                                (io/file (io/resource "project-root/lib/some-other.jar"))
                                                (io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar"))
                                                :in-any-order)))


  (let [app-root (io/file (io/resource "non-project-root"))]
    (fact "read-project should return nil"
      (read-project app-root nil) => nil?)

    (fact "resource-paths should work"
      (resource-paths app-root nil) => (just (map #(.getAbsolutePath (io/file app-root %))
                                                  ["resources" "native" "src" "classes"])
                                             :in-any-order))

    (fact "lib-dir should work"
      (lib-dir app-root nil) => (io/file app-root "lib"))


    (facts "bundled-jars"
      (let [jar-set (bundled-jars (io/file app-root) nil)]
        (fact "should include the jars"
          jar-set => (contains (set (map #(io/file (io/resource %))
                                         ["non-project-root/lib/some.jar"
                                          "non-project-root/lib/some-other.jar"])) :gaps-ok))
        
        (fact "shouldn't include non jars"
          jar-set =not=> (contains (io/file (io/resource "non-project-root/lib/some.txt"))))
        (fact "shouldn't include dev jars"
          jar-set =not=> (io/file (io/resource "non-project-root/lib/dev/invalid.jar")))))
    
    (fact "get-dependencies should return deps from lib"
      (get-dependencies app-root true nil) =>
      (contains (io/file (io/resource "non-project-root/lib/some.jar"))))

    (fact "get-dependencies without resolve-deps should only return jars from lib"
      (get-dependencies app-root false nil) =>
      (just
       (io/file (io/resource "non-project-root/lib/some.jar"))
       (io/file (io/resource "non-project-root/lib/some-other.jar"))
       (io/file (io/resource "non-project-root/lib/tools.logging-0.2.3.jar"))
       :in-any-order)))

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
   #{:foo :bar} [":foo" :bar])
  
  (facts "resolve-dependencies"
    (let [project (read-project (io/file (io/resource "project-root")) nil)
          expected-deps (aether/dependency-files
                         (aether/resolve-dependencies
                          :coordinates (:dependencies project)))]

      (fact "should return the correct deps when there are no unresolvable deps"
        (resolve-dependencies project) => expected-deps)

      (let [project-with-bad-dep (update-in project [:dependencies]
                                            conj ['i-dont-exist "1.0.0"])]
        (fact "should return the no deps when there is an unresolvable dep"
          (resolve-dependencies project-with-bad-dep) => nil))))
    
    
  (facts "read-full-app-config"
    (let [project-root (io/file (io/resource "project-root"))
          non-project-root (io/file (io/resource "non-project-root"))
          descriptor (io/file (io/resource "simple-descriptor.clj"))]
      
      (fact "should work"
        (read-full-app-config descriptor project-root) => {:init "my.namespace/init"
                                                           :ham "biscuit"
                                                           :biscuit "gravy"
                                                           :resolve-dependencies false
                                                           :lein-profiles [:cheese]})
      (fact "should work with no project"
        (read-full-app-config descriptor non-project-root) => {:init "my.namespace/init"
                                                               :ham "biscuit"})
      
      (fact "should work with no descriptor"
        (read-full-app-config nil project-root) => {:ham "basket"
                                                    :biscuit "gravy"
                                                    :init 'some.namespace/init
                                                    :resolve-dependencies false
                                                    :lein-profiles [:cheese]})
      
      (fact "should work with no descriptor and no project"
        (read-full-app-config nil non-project-root) => nil))))


