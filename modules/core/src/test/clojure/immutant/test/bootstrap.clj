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

(ns immutant.test.bootstrap
  (:use immutant.bootstrap
        clojure.test
        immutant.test.helpers)
  (:require [clojure.java.io             :as io]
            [cemerick.pomegranate.aether :as aether]))

(let [app-root (io/file (io/resource "project-root"))]
  (deftest read-project-should-work
    (is (= :biscuit (:ham (read-project app-root)))))

  (deftest read-and-stringify-project-should-work
    (is (= :biscuit ((read-and-stringify-project app-root) "ham"))))

  (deftest resource-paths-should-work
    (is (= (set (map #(.getAbsolutePath (io/file app-root %))
                     ["resources" "native" "src"]))
           (set (resource-paths app-root)))))

  (deftest lib-dir-should-work
    (is (= (io/file app-root "lib") (lib-dir app-root))))
  
  (let [jar-set (bundled-jars (io/file app-root))]
    (deftest bundled-jars-should-find-the-expected-jars
      (is (some (set (map #(io/file (io/resource %))
                          ["project-root/lib/some.jar"
                           "project-root/lib/some-other.jar"]))
                jar-set)))

    (deftest bundled-jars-should-find-not-find-non-jars
      (is-not (some #{(io/file (io/resource "project-root/lib/some.txt"))}
                    jar-set)))

    (deftest bundled-jars-should-find-not-find-dev-jars
      (is-not (some #{(io/file (io/resource "project-root/lib/dev/invalid.jar"))}
                    jar-set))))

  (let [deps (get-dependencies app-root false)]
    (deftest get-dependencies-should-return-deps-from-project-clj
      (is (some #{(first (aether/dependency-files
                          (aether/resolve-dependencies
                           :coordinates [['org.clojure/clojure "1.3.0"]])))}
                deps)))

    (deftest get-dependencies-should-return-deps-from-lib
      (is (some #{(io/file (io/resource "project-root/lib/some.jar"))}
                deps)))

    (deftest get-dependencies-should-exclude-lib-deps-from-resolved-deps
      (is (some #{(io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar"))}
                deps))
      (is-not (some #{(first
                       (filter #(= "tools.logging-0.2.3.jar" (.getName %))
                               (aether/dependency-files
                                (aether/resolve-dependencies
                                 :coordinates [['org.clojure/tools.logging "0.2.3"]]))))}
                    deps))))
  
  (deftest get-dependencies-with-bundled-only-should-only-return-jars-from-lib
    (is (= #{(io/file (io/resource "project-root/lib/some.jar"))
             (io/file (io/resource "project-root/lib/some-other.jar"))
             (io/file (io/resource "project-root/lib/tools.logging-0.2.3.jar"))}
           (set (get-dependencies app-root true))))))




(let [app-root (io/file (io/resource "non-project-root"))]
  (deftest read-project-should-return-nil
    (is (nil? (read-project app-root))))

  (deftest read-and-stringify-project-return-nil
    (is (nil? (read-and-stringify-project app-root))))

  (deftest resource-paths-should-work
    (is (= (set (map #(.getAbsolutePath (io/file app-root %))
                     ["resources" "native" "src"]))
           (set (resource-paths app-root)))))

  (deftest lib-dir-should-work
    (is (= (io/file app-root "lib") (lib-dir app-root))))
  
  (let [jar-set (bundled-jars (io/file app-root))]
    (deftest bundled-jars-should-find-the-expected-jars
      (is (some (set (map #(io/file (io/resource %))
                          ["non-project-root/lib/some.jar"
                           "non-project-root/lib/some-other.jar"]))
                jar-set)))

    (deftest bundled-jars-should-find-not-find-non-jars
      (is-not (some #{(io/file (io/resource "non-project-root/lib/some.txt"))}
                    jar-set)))

    (deftest bundled-jars-should-find-not-find-dev-jars
      (is-not (some #{(io/file (io/resource "non-project-root/lib/dev/invalid.jar"))}
                    jar-set))))

  (let [deps (get-dependencies app-root false)]
    (deftest get-dependencies-should-return-deps-from-lib
      (is (some #{(io/file (io/resource "non-project-root/lib/some.jar"))}
                deps))))
  
  (deftest get-dependencies-with-bundled-only-should-only-return-jars-from-lib
    (is (= #{(io/file (io/resource "non-project-root/lib/some.jar"))
             (io/file (io/resource "non-project-root/lib/some-other.jar"))
             (io/file (io/resource "non-project-root/lib/tools.logging-0.2.3.jar"))}
           (set (get-dependencies app-root true))))))
