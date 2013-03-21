(ns no-clojure-jar.tests
  (:use clojure.test))

(deftest bundled-clojure-version
  (is (= "1.5.1" (clojure-version))))
