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

(ns immutant.runtime.bootstrap-test
  (:use immutant.runtime.bootstrap
        immutant.runtime-util
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

    (fact "read-project with profiles should apply them from profiles.clj"
      (get-in (read-project another-app-root [:ham]) [:immutant :shaq]) => :attaq)

    (fact "read-project without profiles should apply the default profiles profiles.clj"
      (get-in (read-project another-app-root nil) [:immutant :shaq]) => :oneal)
    
    (fact "read-and-stringify-full-app-config should work"
      (read-and-stringify-full-app-config nil app-root) => (contains {"ham" "basket"}))

    (facts "read-and-stringify-full-app-config should stringify an init fn"
      ((read-and-stringify-full-app-config nil app-root) "init") => "some.namespace/init"
      ((read-and-stringify-full-app-config nil another-app-root) "init") => "some.namespace/string")

    (facts "resource-paths"
      (fact "should return the proper paths"
        (let [paths (map #(.getAbsolutePath (io/file app-root %))
                         ["test" "resources" "src" "target/cheese/classes"])]
          (resource-paths app-root nil) => (just paths :in-any-order)))

      (fact "should include checkout deps"
        (let [app-root (io/file (io/resource "project-with-checkout-deps"))
              subdirs #{"test" "resources" "src" "dev-resources"}
              paths (as-> subdirs %
                          (mapcat (fn [d] [(io/file app-root d)
                                          (io/file app-root "checkouts/other-project" d)])
                                  %)
                          (conj %
                                (io/file app-root "target/dev+base+user/classes")
                                (io/file app-root "checkouts/other-project/target/classes"))
                          (map (memfn getAbsolutePath) %))]
          (resource-paths app-root default-profiles) => (just paths :in-any-order))))
    
    (facts "get-dependencies"
      (let [deps (get-dependencies app-root nil true)]

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
      (get-dependencies app-root nil false) => (just
                                                (io/file (io/resource "project-root/lib/some.jar"))
                                                (io/file (io/resource "project-root/lib/some-other.jar"))
                                                (io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar"))
                                                :in-any-order)))


  (let [app-root (io/file (io/resource "non-project-root"))]
    (fact "read-project should return nil"
      (read-project app-root nil) => nil?)

    (fact "resource-paths should work"
      (resource-paths app-root nil) => (just (map #(.getAbsolutePath (io/file app-root %))
                                                  ["test" "resources" "native" "src" "classes"])
                                             :in-any-order))
    
    (fact "get-dependencies should return deps from lib"
      (get-dependencies app-root nil true) =>
      (contains (io/file (io/resource "non-project-root/lib/some.jar"))))

    (fact "get-dependencies without resolve-deps should only return jars from lib"
      (get-dependencies app-root nil false) =>
      (just
       (io/file (io/resource "non-project-root/lib/some.jar"))
       (io/file (io/resource "non-project-root/lib/some-other.jar"))
       (io/file (io/resource "non-project-root/lib/tools.logging-0.2.3.jar"))
       :in-any-order)))

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
          (resolve-dependencies project-with-bad-dep) => nil))

      (fact "should ignore immutant deps"
        (let [project-with-immutant-deps
              (update-in project [:dependencies]
                         conj ['org.immutant/immutant "0.6.0"] ['org.immutant/immutant-web "0.6.0"])]
          (resolve-dependencies project-with-immutant-deps) => expected-deps))

      (fact "should ignore transitive immutant deps"
        (let [project-with-transitive-immutant-deps
              (update-in project [:dependencies]
                         conj ['org.immutant/transitive-immutant-deps-test-lib "0.1.0"])
              expected (-> (map (memfn getName) expected-deps)
                           (conj "transitive-immutant-deps-test-lib-0.1.0.jar")
                           set)
              actual (->> (resolve-dependencies project-with-transitive-immutant-deps)
                          (map (memfn getName))
                          set)]
          actual => expected))))
    
  (facts "read-full-app-config"
    (let [project-root (io/file (io/resource "project-root"))
          non-project-root (io/file (io/resource "non-project-root"))
          id-project-root (io/file (io/resource "id-project-root"))
          id-non-project-root (io/file (io/resource "id-non-project-root"))
          descriptor (io/file (io/resource "simple-descriptor.clj"))]

      (facts "with a project"
        (fact "should work"
          (read-full-app-config descriptor project-root) => {:init "my.namespace/init"
                                                             :ham "biscuit"
                                                             :biscuit "gravy"
                                                             :resolve-dependencies false
                                                             :lein-profiles [:cheese]})

        (fact "should work with an internal dd"
          (read-full-app-config descriptor id-project-root) => {:init "my.namespace/init"
                                                                :ham "biscuit"
                                                                :biscuit "gravy"
                                                                :resolve-dependencies false
                                                                :lein-profiles [:biscuit :gravy]})
        (fact "should work with no descriptor"
          (read-full-app-config nil project-root) => {:ham "basket"
                                                      :biscuit "gravy"
                                                      :init 'some.namespace/init
                                                      :resolve-dependencies false
                                                      :lein-profiles [:cheese]})

        (fact "should work with no descriptor and an internal dd"
          (read-full-app-config nil id-project-root) => {:ham "basket"
                                                         :biscuit "gravy"
                                                         :init 'some.namespace/init
                                                         :resolve-dependencies false
                                                         :lein-profiles [:biscuit :gravy]}))

      (facts "with no project"
        (fact "should work"
          (read-full-app-config descriptor non-project-root) => {:init "my.namespace/init"
                                                                 :ham "biscuit"})

        (fact "should work with an internal dd"
          (read-full-app-config descriptor id-non-project-root) => {:init "my.namespace/init"
                                                                    :ham "biscuit"
                                                                    :lein-profiles [:biscuit :gravy]}))
      

      
      (fact "should work with no descriptor and no project"
        (read-full-app-config nil non-project-root) => {}))))


